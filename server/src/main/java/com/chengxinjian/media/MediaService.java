package com.chengxinjian.media;

import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.auth.TokenSecrets;
import com.chengxinjian.common.AuditService;
import com.chengxinjian.common.BusinessException;
import com.chengxinjian.user.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.chengxinjian.media.MediaModels.*;

@Service
public class MediaService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final AuditService audit;
    private final MediaStorage storage;
    private final MediaProperties properties;
    private final TransactionTemplate tx;
    private final RowMapper<Asset> mapper=(r,n)->new Asset(r.getString("id"),r.getString("purpose"),r.getString("filename"),r.getString("mime_type"),
        r.getLong("size_bytes"),(Integer)r.getObject("duration_seconds"),r.getString("status"),"approved".equals(r.getString("review_status")) ? r.getString("public_url") : null,r.getInt("version"),
        r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
    public MediaService(JdbcTemplate jdbc,ObjectMapper json,Clock clock,AuditService audit,MediaStorage storage,MediaProperties properties,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.json=json;this.clock=clock;this.audit=audit;this.storage=storage;this.properties=properties;
        tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    record Actor(String id,String audience,AdminPrincipal admin) {}
    private Actor actor(Object principal) {
        if (principal instanceof UserPrincipal user) return new Actor(user.id(),"user",null);
        if (principal instanceof AdminPrincipal admin) return new Actor(admin.id(),"admin",admin);
        throw BusinessException.unauthenticated();
    }
    private void permission(Actor actor,boolean write) {
        if (actor.admin()==null) return;
        var admin=actor.admin();
        if (!admin.permissions().contains(write ? "content:write" : "content:read")) throw BusinessException.forbidden();
        if (!"all".equals(admin.dataScope())) {
            if (!"own_content".equals(admin.dataScope()) || admin.teacherId()==null || jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_teacher WHERE id=? AND status<>'archived'",Long.class,admin.teacherId())!=1) throw BusinessException.forbidden();
        }
    }
    Asset lockEditorial(AdminPrincipal admin,String id) { own(actor(admin),row(id,true),true); return find(id); }
    void requireEditorialScope(AdminPrincipal admin) { permission(actor(admin),false); }
    private void own(Actor actor,Map<String,Object> row,boolean write) {
        permission(actor,write);
        if (actor.admin()==null) {
            if (!actor.id().equals(row.get("owner_user_id"))) throw BusinessException.notFound();
        } else if (row.get("owner_admin_id")==null || (!"all".equals(actor.admin().dataScope()) && !actor.id().equals(row.get("owner_admin_id")))) throw BusinessException.forbidden();
    }
    private Map<String,Object> row(String id,boolean lock) {
        var rows=jdbc.queryForList("SELECT * FROM media_asset WHERE id=?"+(lock ? " FOR UPDATE" : ""),id);
        return rows.stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    public Asset get(Object principal,String id) { own(actor(principal),row(id,false),false);return find(id); }
    private Asset find(String id) { return jdbc.queryForObject("SELECT * FROM media_asset WHERE id=?",mapper,id); }
    private void validate(Actor actor,Upload input) {
        permission(actor,true);
        if (actor.admin()==null && !Set.of("avatar","practice_recording").contains(input.purpose())) throw BusinessException.forbidden();
        if (actor.admin()!=null && "practice_recording".equals(input.purpose())) throw BusinessException.forbidden();
        if (input.filename().contains("/") || input.filename().contains("\\") || input.filename().codePoints().anyMatch(Character::isISOControl)) throw BusinessException.invalid("文件名不合法");
        Set<String> images=Set.of("image/png","image/jpeg","image/webp"), audio=Set.of("audio/mpeg","audio/mp4","audio/wav"), video=Set.of("video/mp4","video/webm");
        boolean image=Set.of("avatar","cover").contains(input.purpose());
        if (image ? !images.contains(input.mimeType()) : !(audio.contains(input.mimeType()) || (Set.of("course_media","practice_recording").contains(input.purpose()) && video.contains(input.mimeType())))) throw BusinessException.invalid("不支持该用途的文件类型");
        long max=image ? 10L*1024*1024 : properties.maxMediaBytes();
        if (input.sizeBytes()>max) throw BusinessException.invalid("文件超过上传大小限制");
        if (!image && (properties.ffmpegPath()==null || properties.ffmpegPath().isBlank() || !java.nio.file.Files.isExecutable(java.nio.file.Path.of(properties.ffmpegPath())))) throw MediaErrors.unavailable();
    }
    private void key(String key) {
        if (key==null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw BusinessException.invalid("Idempotency-Key 必须为 UUID");
    }
    record Reservation(String assetId,String objectKey,Instant expires,String lease,String ticket) {}
    public Ticket create(Object principal,String key,Upload input) {
        key(key);var actor=actor(principal);validate(actor,input);String hash=TokenSecrets.hash(encode(input));
        var reservation=tx.execute(status->{
            jdbc.queryForObject("SELECT id FROM "+(actor.admin()==null ? "app_user" : "admin_account")+" WHERE id=? FOR UPDATE",String.class,actor.id());
            var rows=jdbc.queryForList("SELECT * FROM media_upload_request WHERE actor_id=? AND audience=? AND idempotency_key=? FOR UPDATE",actor.id(),actor.audience(),key);
            String id;Instant expires;
            if (!rows.isEmpty()) {
                var existing=rows.getFirst();
                if (!hash.equals(existing.get("request_hash"))) throw MediaErrors.conflict("IDEMPOTENCY_CONFLICT","相同幂等标识不能用于不同请求");
                id=(String)existing.get("asset_id");var media=row(id,false);expires=((Timestamp)media.get("upload_expires_at")).toInstant();
                if (existing.get("ticket_json")!=null) return new Reservation(id,(String)media.get("quarantine_key"),expires,null,(String)existing.get("ticket_json"));
                if (!expires.isAfter(clock.instant())) throw MediaErrors.conflict("UPLOAD_EXPIRED","上传申请已过期，请使用新的幂等标识申请");
                if (existing.get("lease_until") instanceof Timestamp until && until.toInstant().isAfter(clock.instant())) throw MediaErrors.conflict("UPLOAD_TICKET_PENDING","上传凭据正在生成，请稍后使用相同标识重试");
            } else {
                String owner=actor.admin()==null ? "owner_user_id" : "owner_admin_id";
                if (jdbc.queryForObject("SELECT COUNT(*) FROM media_asset WHERE "+owner+"=? AND status IN ('uploading','processing') AND created_at>?",Long.class,actor.id(),Timestamp.from(clock.instant().minusSeconds(3600)))>=20) throw MediaErrors.conflict("UPLOAD_LIMIT_REACHED","待处理文件过多，请稍后重试");
                id=UUID.randomUUID().toString();expires=clock.instant().plusSeconds(600);String quarantine="quarantine/"+id+"/source";
                jdbc.update("INSERT INTO media_asset(id,purpose,filename,mime_type,size_bytes,sha256,object_key,status,"+owner+",quarantine_key,upload_expires_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'uploading',?,?,?,?,?)",
                    id,input.purpose(),input.filename(),input.mimeType(),input.sizeBytes(),input.sha256(),quarantine,actor.id(),quarantine,Timestamp.from(expires),Timestamp.from(clock.instant()),Timestamp.from(clock.instant()));
                jdbc.update("INSERT INTO media_upload_request(actor_id,audience,idempotency_key,request_hash,asset_id) VALUES (?,?,?,?,?)",actor.id(),actor.audience(),key,hash,id);
                audit.record(actor.id(),"media.upload.request",id,null);
            }
            String lease=UUID.randomUUID().toString();
            jdbc.update("UPDATE media_upload_request SET lease_token=?,lease_until=? WHERE asset_id=?",lease,Timestamp.from(clock.instant().plusSeconds(60)),id);
            return new Reservation(id,"quarantine/"+id+"/source",expires,lease,null);
        });
        if (reservation.ticket()!=null) return decode(reservation.ticket(),Ticket.class);
        try {
            Ticket ticket=storage.ticket(reservation.assetId(),reservation.objectKey(),input.mimeType(),input.sizeBytes(),reservation.expires());
            int saved=jdbc.update("UPDATE media_upload_request SET ticket_json=?,lease_token=NULL,lease_until=NULL WHERE asset_id=? AND lease_token=?",encode(ticket),reservation.assetId(),reservation.lease());
            if (saved!=1) throw MediaErrors.conflict("UPLOAD_TICKET_PENDING","上传凭据正在生成，请稍后重试");
            return ticket;
        } catch (RuntimeException e) {
            jdbc.update("UPDATE media_upload_request SET lease_token=NULL,lease_until=NULL WHERE asset_id=? AND lease_token=?",reservation.assetId(),reservation.lease());throw e;
        }
    }
    public Asset complete(Object principal,String id,String key,Complete input) {
        key(key);var actor=actor(principal);
        return tx.execute(status->{
            var row=row(id,true);own(actor,row,true);String operation="POST /"+actor.audience()+"/uploads/"+id+"/complete",hash=TokenSecrets.hash(encode(input));
            var old=jdbc.queryForList("SELECT request_hash,result_json FROM idempotency_record WHERE actor_id=? AND operation=? AND idempotency_key=?",actor.id(),operation,key);
            if (!old.isEmpty()) {
                if (!hash.equals(old.getFirst().get("request_hash"))) throw MediaErrors.conflict("IDEMPOTENCY_CONFLICT","相同幂等标识不能用于不同请求");
                return decode((String)old.getFirst().get("result_json"),Asset.class);
            }
            if (!input.sha256().equals(row.get("sha256"))) throw MediaErrors.conflict("UPLOAD_HASH_MISMATCH","文件摘要与申请不一致");
            if ("rejected".equals(row.get("status"))) throw MediaErrors.conflict("ASSET_REJECTED","文件校验未通过，请重新上传");
            if ("uploading".equals(row.get("status"))) {
                if (!((Timestamp)row.get("upload_expires_at")).toInstant().isAfter(clock.instant())) throw MediaErrors.conflict("UPLOAD_EXPIRED","上传申请已过期");
                jdbc.update("UPDATE media_asset SET status='processing',version=version+1,updated_at=? WHERE id=?",Timestamp.from(clock.instant()),id);
                jdbc.update("INSERT INTO media_processing_job(asset_id,state,next_attempt_at,updated_at) VALUES (?,'pending',?,?)",id,Timestamp.from(clock.instant()),Timestamp.from(clock.instant()));
                audit.record(actor.id(),"media.upload.complete",id,null);
            } else if ("processing".equals(row.get("status"))) {
                jdbc.update("UPDATE media_processing_job SET state='pending',attempts=0,next_attempt_at=?,updated_at=? WHERE asset_id=? AND state='failed'",Timestamp.from(clock.instant()),Timestamp.from(clock.instant()),id);
            }
            Asset result=find(id);
            jdbc.update("INSERT INTO idempotency_record(actor_id,operation,idempotency_key,request_hash,result_id,result_json,created_at) VALUES (?,?,?,?,?,?,?)",actor.id(),operation,key,hash,id,encode(result),Timestamp.from(clock.instant()));
            return result;
        });
    }
    public Preview preview(AdminPrincipal admin,String id) {
        var row=row(id,false);own(actor(admin),row,false);
        if (!"ready".equals(row.get("status"))) throw MediaErrors.conflict("ASSET_NOT_READY","文件尚未完成校验");
        Instant expires=clock.instant().plusSeconds(120);
        return new Preview(storage.preview((String)row.get("object_key"),expires),expires);
    }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Media serialization failed"); } }
    private <T> T decode(String value,Class<T> type) { try { return json.readValue(value,type); } catch (Exception e) { throw new IllegalStateException("Media snapshot invalid"); } }
}
