package com.chengxinjian.media;

import java.io.IOException;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LessonTrialWorker {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final MediaStorage storage;
    private final MediaProperties config;
    public LessonTrialWorker(JdbcTemplate jdbc,Clock clock,MediaStorage storage,MediaProperties config) {
        this.jdbc=jdbc; this.clock=clock; this.storage=storage; this.config=config;
    }
    @Scheduled(fixedDelayString="${app.media.trial-delay-ms:2000}")
    public void poll() { if (config.workerEnabled()) runOnce(); }
    private Timestamp after(long seconds) { return Timestamp.from(clock.instant().plusSeconds(seconds)); }
    public boolean runOnce() {
        var rows=jdbc.queryForList("""
            SELECT tr.asset_id,tr.seconds,a.object_key,a.size_bytes,a.mime_type FROM lesson_trial tr JOIN media_asset a ON a.id=tr.asset_id
            WHERE tr.state IN ('pending','running') AND tr.next_attempt_at<=? AND a.status='ready' AND a.review_status='approved'
            ORDER BY tr.next_attempt_at,tr.asset_id,tr.seconds LIMIT 1
            """,after(0));
        if (rows.isEmpty()) return false;
        var row=rows.getFirst();
        String id=(String)row.get("asset_id"),lease=UUID.randomUUID().toString(),mime=(String)row.get("mime_type");
        int seconds=((Number)row.get("seconds")).intValue();
        int claimed=jdbc.update("""
            UPDATE lesson_trial SET state='running',lease_token=?,attempts=attempts+1,next_attempt_at=?
            WHERE asset_id=? AND seconds=? AND state IN ('pending','running') AND next_attempt_at<=?
            """,lease,after(600),id,seconds,after(0));
        if (claimed==0) return false;
        boolean video=mime.startsWith("video/");
        String key="trials/"+id+"/"+seconds+(video?".mp4":".m4a");
        Path source=null,output=null;
        try {
            source=Files.createTempFile("cxj-trial-source-",".bin"); output=Files.createTempFile("cxj-trial-",video?".mp4":".m4a");
            storage.download((String)row.get("object_key"),source,((Number)row.get("size_bytes")).longValue(),mime);
            encode(source,output,video,seconds);
            storage.put(key,output,video?"video/mp4":"audio/mp4");
            jdbc.update("UPDATE lesson_trial SET state='ready',object_key=?,error_code=NULL,lease_token=NULL WHERE asset_id=? AND seconds=? AND lease_token=?",key,id,seconds,lease);
        } catch (IOException | RuntimeException ex) {
            jdbc.update("""
                UPDATE lesson_trial SET state=CASE WHEN attempts>=5 THEN 'failed' ELSE 'pending' END,
                error_code='TRIAL_PROCESSING_FAILED',next_attempt_at=?,lease_token=NULL WHERE asset_id=? AND seconds=? AND lease_token=?
                """,after(30),id,seconds,lease);
        } finally { remove(source); remove(output); }
        return true;
    }
    private void encode(Path source,Path output,boolean video,int seconds) throws IOException {
        var args=new ArrayList<>(List.of(config.ffmpegPath(),"-nostdin","-y","-hide_banner","-loglevel","error","-xerror",
            "-protocol_whitelist","file,pipe","-format_whitelist","mov,mp4,m4a,3gp,3g2,mj2,mp3,wav,matroska,webm",
            "-max_alloc","67108864","-max_pixels","16000000","-threads","1","-i",source.toString(),"-t",Integer.toString(seconds),
            "-map_metadata","-1","-map_chapters","-1","-sn","-dn","-threads","1","-filter_threads","1"));
        if (video) args.addAll(List.of("-map","0:V:0","-map","0:a:0?","-vf","scale='min(960,iw)':-2:force_divisible_by=2","-c:v","libx264","-pix_fmt","yuv420p"));
        else args.addAll(List.of("-map","0:a:0","-vn"));
        args.addAll(List.of("-c:a","aac","-b:a","96k","-movflags","+faststart","-fs",Long.toString(config.maxMediaBytes()),output.toString()));
        var builder=new ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear(); builder.environment().put("LANG","C");
        var process=builder.start();
        try {
            if (!process.waitFor(config.decodeTimeoutSeconds(),TimeUnit.SECONDS) || process.exitValue()!=0 ||
                Files.size(output)==0 || Files.size(output)>=config.maxMediaBytes()) throw new IOException("Trial encoding failed");
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException("Trial encoding interrupted"); }
        finally { if (process.isAlive()) process.destroyForcibly(); }
    }
    private void remove(Path file) {
        if (file!=null) try { Files.deleteIfExists(file); } catch(IOException ex) { file.toFile().deleteOnExit(); }
    }
}
