package com.chengxinjian.catalog;

import com.chengxinjian.auth.TokenSecrets;
import com.chengxinjian.common.*;
import com.chengxinjian.media.MediaStorage;
import com.chengxinjian.user.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static com.chengxinjian.catalog.CourseModels.Access;

@RestController
@RequestMapping("/api/v1/playbacks")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class LessonPlayback {
    public record Target(@NotBlank String kind,@NotBlank @Size(max=36) String id) {}
    public record Request(@NotNull @Valid Target target,@NotNull @Pattern(regexp="wechat_android|wechat_ios|wechat_harmony|wechat_windows") String platform) {}
    public record Ticket(String playbackId,String url,Instant expiresAt,int durationSeconds,int resumeSeconds,Access access,String mediaKind,int heartbeatSeconds) {
        @Override public String toString() { return "Ticket[private]"; }
    }
    public record Event(@NotBlank @Size(max=36) String eventId,@NotNull @Positive Integer sequence,
        @NotNull @Pattern(regexp="playing|pause|seek|ended|background|foreground") String kind,
        @NotNull @PositiveOrZero Integer positionSeconds,@NotNull @PositiveOrZero Integer playedDeltaSeconds,
        @NotNull Double speed,@NotNull Instant occurredAt) {}
    public record Ack(int acceptedSequence,int positionSeconds,int creditedSeconds,boolean completed) {}
    private record Source(String asset,String key,String mediaKind,int seconds,Access access) {}
    private final MediaStorage storage;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Entitlements grants;
    private final UserService users;
    private final ObjectMapper json;
    public LessonPlayback(MediaStorage storage,JdbcTemplate jdbc,Clock clock,Entitlements grants,UserService users,ObjectMapper json) {
        this.storage=storage; this.jdbc=jdbc; this.clock=clock; this.grants=grants; this.users=users; this.json=json;
    }
    private static void key(String value) {
        try { if (!UUID.fromString(value).toString().equalsIgnoreCase(value)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException ex) { throw BusinessException.invalid("幂等标识须为 UUID"); }
    }
    private BusinessException expired() { return new BusinessException(HttpStatus.GONE,"PLAYBACK_EXPIRED","请重新申请播放授权"); }
    private BusinessException duplicate() { return new BusinessException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","重复请求与原记录不同"); }
    private Source authorize(UserPrincipal user,String lesson) {
        // All publication and moderation predicates are shared with the public catalog.
        var row=jdbc.queryForList("""
            SELECT l.asset_id,l.course_id,l.trial_seconds,a.duration_seconds,a.mime_type,a.object_key,c.access_policy,tr.object_key AS trial_key
            """+CourseCatalog.LESSON_FROM+" LEFT JOIN lesson_trial tr ON tr.asset_id=l.asset_id AND tr.seconds=l.trial_seconds AND tr.state='ready' WHERE l.id=? AND "+
            CourseCatalog.COURSE_VISIBLE+" AND "+CourseCatalog.LESSON_VISIBLE,lesson).stream().findFirst().orElseThrow(BusinessException::notFound);
        var access=grants.access(user,(String)row.get("course_id"),(String)row.get("access_policy"),
            row.get("trial_key")==null ? 0 : ((Number)row.get("trial_seconds")).intValue());
        if (!access.allowed()) throw new BusinessException(HttpStatus.FORBIDDEN,"CONTENT_LOCKED","尚未取得此课节的播放权益");
        boolean trial="trial".equals(access.reason());
        return new Source((String)row.get("asset_id"),(String)row.get(trial?"trial_key":"object_key"),
            row.get("mime_type").toString().startsWith("audio/")?"audio":"video",trial?access.trialSeconds():((Number)row.get("duration_seconds")).intValue(),access);
    }
    @PostMapping
    public ApiResponse<Ticket> create(@AuthenticationPrincipal UserPrincipal user,@RequestHeader("Idempotency-Key") String key,@RequestBody @Valid Request input) {
        key(key);
        if (!"lesson".equals(input.target().kind())) throw BusinessException.invalid("此阶段支持课节播放");
        users.requireActive(user.id(),true);
        String lesson=input.target().id(),hash=hash(input);
        var existing=jdbc.queryForList("SELECT * FROM lesson_playback WHERE user_id=? AND idempotency_key=?",user.id(),key);
        if (!existing.isEmpty()) {
            var row=existing.getFirst();
            if (!hash.equals(row.get("request_hash"))) throw duplicate();
            var source=current(user,row);
            return ApiResponse.ok(snapshot((String)row.get("id"),"create",key,source).orElseThrow(this::expired));
        }
        var source=authorize(user,lesson);
        String id=UUID.randomUUID().toString(); Instant expiry=expiry(source);
        int position=jdbc.queryForList("SELECT position_seconds FROM lesson_progress WHERE user_id=? AND lesson_id=? AND asset_id=?",
            Integer.class,user.id(),lesson,source.asset()).stream().findFirst().orElse(0);
        position=Math.min(position,source.seconds());
        jdbc.update("""
            INSERT INTO lesson_playback(id,user_id,lesson_id,asset_id,access_reason,allowed_seconds,expires_at,last_event_at,position_seconds,idempotency_key,request_hash)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """,id,user.id(),lesson,source.asset(),source.access().reason(),source.seconds(),Timestamp.from(expiry),Timestamp.from(clock.instant()),position,key,hash);
        return ApiResponse.ok(issue(id,"create",key,source,expiry,position));
    }
    private Instant expiry(Source source) {
        Instant end=clock.instant().plusSeconds(120),grantEnd=source.access().expiresAt();
        return grantEnd!=null && grantEnd.isBefore(end) ? grantEnd : end;
    }
    private Map<String,Object> session(UserPrincipal user,String id) {
        users.requireActive(user.id(),true);
        return jdbc.queryForList("SELECT * FROM lesson_playback WHERE id=? AND user_id=? FOR UPDATE",id,user.id())
            .stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    private Source current(UserPrincipal user,Map<String,Object> row) {
        if (!((Timestamp)row.get("expires_at")).toInstant().isAfter(clock.instant())) throw expired();
        var source=authorize(user,(String)row.get("lesson_id"));
        if (!source.asset().equals(row.get("asset_id")) || !source.access().reason().equals(row.get("access_reason")) ||
            source.seconds()!=((Number)row.get("allowed_seconds")).intValue()) throw expired();
        return source;
    }
    @PostMapping("/{id}/renew")
    public ApiResponse<Ticket> renew(@AuthenticationPrincipal UserPrincipal user,@PathVariable String id,@RequestHeader("Idempotency-Key") String key) {
        key(key); var row=session(user,id); var source=current(user,row);
        var saved=snapshot(id,"renew",key,source);
        if (saved.isPresent()) return ApiResponse.ok(saved.get());
        var expiry=expiry(source);
        jdbc.update("UPDATE lesson_playback SET expires_at=? WHERE id=?",Timestamp.from(expiry),id);
        return ApiResponse.ok(issue(id,"renew",key,source,expiry,((Number)row.get("position_seconds")).intValue()));
    }
    private Optional<Ticket> snapshot(String id,String op,String key,Source source) {
        return jdbc.query("SELECT response_json FROM playback_ticket_snapshot WHERE playback_id=? AND operation=? AND idempotency_key=?",
            (r,n)->read(r.getString(1),Ticket.class),id,op,key).stream().findFirst().map(ticket->{
                if (!ticket.expiresAt().isAfter(clock.instant()) || source.access().expiresAt()!=null && ticket.expiresAt().isAfter(source.access().expiresAt())) throw expired();
                return ticket;
            });
    }
    private Ticket issue(String id,String op,String key,Source source,Instant expiry,int position) {
        var ticket=new Ticket(id,storage.preview(source.key(),expiry),expiry,source.seconds(),position,source.access(),source.mediaKind(),20);
        jdbc.update("INSERT INTO playback_ticket_snapshot(playback_id,operation,idempotency_key,response_json,expires_at) VALUES (?,?,?,?,?)",
            id,op,key,encode(ticket),Timestamp.from(expiry));
        return ticket;
    }
    @PostMapping("/{id}/events")
    public ApiResponse<Ack> event(@AuthenticationPrincipal UserPrincipal user,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody @Valid Event input) {
        key(key);
        var row=session(user,id); String hash=hash(input);
        var existing=jdbc.queryForList("SELECT request_hash,ack_json FROM lesson_playback_event WHERE playback_id=? AND (event_id=? OR idempotency_key=? OR sequence_no=?)",
            id,input.eventId(),key,input.sequence());
        if (!existing.isEmpty()) {
            if (existing.size()!=1 || !hash.equals(existing.getFirst().get("request_hash"))) throw duplicate();
            return ApiResponse.ok(read((String)existing.getFirst().get("ack_json"),Ack.class));
        }
        var source=current(user,row);
        int previousSequence=((Number)row.get("sequence_no")).intValue(),previousPosition=((Number)row.get("position_seconds")).intValue();
        if (input.sequence()<=previousSequence) throw new BusinessException(HttpStatus.CONFLICT,"PLAYBACK_SEQUENCE_CONFLICT","播放事件序号已过期");
        if (input.positionSeconds()>source.seconds() || !Set.of(0.5,0.75,1.0,1.25,1.5,2.0).contains(input.speed()))
            throw BusinessException.invalid("进度或倍速无效");
        Instant now=clock.instant(),last=((Timestamp)row.get("last_event_at")).toInstant();
        if (input.occurredAt().isAfter(now.plusSeconds(30))) throw BusinessException.invalid("播放事件时间无效");
        String lesson=(String)row.get("lesson_id");
        var progress=jdbc.queryForList("SELECT * FROM lesson_progress WHERE user_id=? AND lesson_id=? AND asset_id=?",user.id(),lesson,source.asset());
        int credited=0; boolean completed=false;
        if (!progress.isEmpty()) {
            var p=progress.getFirst(); credited=((Number)p.get("credited_seconds")).intValue(); completed=(Boolean)p.get("completed");
            Instant lastProgress=((Timestamp)p.get("last_played_at")).toInstant();
            if(lastProgress.isAfter(last)) last=lastProgress;
        }
        long wall=Math.max(0,Duration.between(last,now).toSeconds());
        int moved=Math.max(0,input.positionSeconds()-previousPosition);
        int delta=Set.of("seek","foreground").contains(input.kind()) ? 0 : (int)Math.min(Math.min(moved,input.playedDeltaSeconds()),wall*input.speed());
        int fullDuration=jdbc.queryForObject("SELECT duration_seconds FROM media_asset WHERE id=?",Integer.class,source.asset());
        credited=Math.max(credited,(int)Math.min(source.seconds(),(long)credited+delta));
        completed=completed || (!"trial".equals(source.access().reason()) && credited>=fullDuration);
        int position=input.positionSeconds();
        jdbc.update("""
            INSERT INTO lesson_progress(user_id,lesson_id,asset_id,position_seconds,credited_seconds,completed,last_played_at) VALUES (?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE asset_id=?,position_seconds=?,credited_seconds=?,completed=?,last_played_at=?
            """,user.id(),lesson,source.asset(),position,credited,completed,Timestamp.from(now),source.asset(),position,credited,completed,Timestamp.from(now));
        jdbc.update("UPDATE lesson_playback SET sequence_no=?,position_seconds=?,last_event_at=? WHERE id=?",input.sequence(),position,Timestamp.from(now),id);
        var ack=new Ack(input.sequence(),position,credited,completed);
        jdbc.update("INSERT INTO lesson_playback_event(playback_id,event_id,sequence_no,idempotency_key,request_hash,ack_json) VALUES (?,?,?,?,?,?)",
            id,input.eventId(),input.sequence(),key,hash,encode(ack));
        return ApiResponse.ok(ack);
    }
    private String hash(Object o) { return TokenSecrets.hash(encode(o)); }
    private String encode(Object o) { try { return json.writeValueAsString(o); } catch(java.io.IOException ex) { throw new IllegalStateException("Invalid playback payload"); } }
    private <T> T read(String s,Class<T> type) { try { return json.readValue(s,type); } catch(java.io.IOException ex) { throw new IllegalStateException("Invalid playback snapshot"); } }
}
