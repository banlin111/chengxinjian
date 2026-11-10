package com.chengxinjian.media;

import com.chengxinjian.common.AuditService;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@EnableScheduling
public class MediaWorker {
    private static final Logger log=LoggerFactory.getLogger(MediaWorker.class);
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final MediaStorage storage;
    private final MediaInspector inspector;
    private final MediaProperties properties;
    private final AuditService audit;
    private final TransactionTemplate tx;
    public MediaWorker(JdbcTemplate jdbc,Clock clock,MediaStorage storage,MediaInspector inspector,MediaProperties properties,AuditService audit,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.clock=clock;this.storage=storage;this.inspector=inspector;this.properties=properties;this.audit=audit;
        tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    @Scheduled(fixedDelayString="${app.media.worker-delay-ms:1000}")
    public void scheduled() {
        if(!properties.workerEnabled())return;
        try { runOnce();cleanupOnce(); }
        catch(Exception e) { log.error("Media worker unavailable: {}",e.getClass().getSimpleName()); }
    }
    public boolean runOnce() {
        var candidates=jdbc.queryForList("SELECT asset_id FROM media_processing_job WHERE (state='pending' AND next_attempt_at<=?) OR (state='running' AND lease_until<=?) ORDER BY next_attempt_at LIMIT 1",String.class,now(),now());
        if(candidates.isEmpty())return false;String id=candidates.getFirst(),lease=UUID.randomUUID().toString();
        var claimed=tx.execute(status->{
            var job=jdbc.queryForMap("SELECT * FROM media_processing_job WHERE asset_id=? FOR UPDATE",id);
            if(("running".equals(job.get("state")) && ((Timestamp)job.get("lease_until")).toInstant().isAfter(clock.instant())) ||
                (!Set.of("pending","running").contains(job.get("state"))) || ("pending".equals(job.get("state")) && ((Timestamp)job.get("next_attempt_at")).toInstant().isAfter(clock.instant())))return false;
            jdbc.update("UPDATE media_processing_job SET state='running',lease_token=?,lease_until=?,attempts=attempts+1,updated_at=? WHERE asset_id=?",lease,Timestamp.from(clock.instant().plusSeconds(600)),now(),id);return true;
        });
        if(!Boolean.TRUE.equals(claimed))return false;
        Path file=null;
        try {
            var asset=jdbc.queryForMap("SELECT * FROM media_asset WHERE id=?",id);file=Files.createTempFile("cxj-media-",".source");
            String mime=(String)asset.get("mime_type");long size=((Number)asset.get("size_bytes")).longValue();
            storage.download((String)asset.get("quarantine_key"),file,size,mime);
            Integer duration=inspector.inspect(file,mime,size,(String)asset.get("sha256"));
            String finalKey="validated/"+id+"/source";storage.put(finalKey,file,mime);
            finish(id,lease,finalKey,duration,null);
        } catch(MediaErrors.Invalid e) { finish(id,lease,null,null,e.code); }
        catch(Exception e) { retry(id,lease); }
        finally { if(file!=null)try { Files.deleteIfExists(file); } catch(Exception ignored) { log.error("Media temporary cleanup failed"); } }
        return true;
    }
    private void finish(String id,String lease,String key,Integer duration,String error) {
        tx.executeWithoutResult(status->{
            // Completion takes the asset lock before the job lock; use the same
            // order so concurrent complete/retry cannot deadlock promotion.
            jdbc.queryForObject("SELECT id FROM media_asset WHERE id=? FOR UPDATE",String.class,id);
            var job=jdbc.queryForMap("SELECT * FROM media_processing_job WHERE asset_id=? FOR UPDATE",id);
            if(!lease.equals(job.get("lease_token")))return;
            String owner=jdbc.queryForObject("SELECT COALESCE(owner_user_id,owner_admin_id) FROM media_asset WHERE id=?",String.class,id);
            if(error==null)jdbc.update("UPDATE media_asset SET object_key=?,status='ready',duration_seconds=?,failure_code=NULL,version=version+1,updated_at=? WHERE id=? AND status='processing'",key,duration,now(),id);
            else jdbc.update("UPDATE media_asset SET status='rejected',failure_code=?,version=version+1,updated_at=? WHERE id=? AND status='processing'",error,now(),id);
            jdbc.update("UPDATE media_processing_job SET state='done',lease_token=NULL,lease_until=NULL,last_error=?,updated_at=? WHERE asset_id=?",error,now(),id);
            audit.record(owner,error==null ? "media.validation.ready" : "media.validation.rejected",id,error);
        });
    }
    private void retry(String id,String lease) {
        tx.executeWithoutResult(status->{
            var job=jdbc.queryForMap("SELECT * FROM media_processing_job WHERE asset_id=? FOR UPDATE",id);
            if(!lease.equals(job.get("lease_token")))return;
            int attempts=((Number)job.get("attempts")).intValue();
            jdbc.update("UPDATE media_processing_job SET state=?,lease_token=NULL,lease_until=NULL,next_attempt_at=?,last_error='MEDIA_PROVIDER_UNAVAILABLE',updated_at=? WHERE asset_id=?",
                attempts>=5 ? "failed" : "pending",Timestamp.from(clock.instant().plusSeconds(Math.min(300,5L<<attempts))),now(),id);
            log.warn("Media processing retry: assetId={}, attempt={}",id,attempts);
        });
    }
    public void cleanupOnce() {
        var ids=jdbc.queryForList("SELECT id FROM media_asset WHERE quarantine_cleaned=FALSE AND upload_expires_at<? AND status IN ('uploading','ready','rejected') LIMIT 10",String.class,Timestamp.from(clock.instant().minusSeconds(900)));
        for(String id:ids) {
            var row=jdbc.queryForMap("SELECT * FROM media_asset WHERE id=?",id);
            storage.delete((String)row.get("quarantine_key"));
            // Only purge the isolation object. Historical asset rows and validated objects are retained.
            jdbc.update("UPDATE media_asset SET quarantine_cleaned=TRUE,failure_code=CASE WHEN status='uploading' THEN 'UPLOAD_EXPIRED' ELSE failure_code END,status=CASE WHEN status='uploading' THEN 'rejected' ELSE status END,version=version+1,updated_at=? WHERE id=? AND status IN ('uploading','ready','rejected')",now(),id);
            jdbc.update("UPDATE media_upload_request SET ticket_json=NULL WHERE asset_id=?",id);
        }
    }
    private Timestamp now() { return Timestamp.from(clock.instant()); }
}
