package com.chengxinjian.media;

import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.catalog.CategoryModels.Page;
import com.chengxinjian.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class EditorialMedia {
    private final MediaService media;
    private final MediaStorage storage;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final CreateSnapshot snapshots;
    private final ObjectMapper json;
    private final String origin;
    private final boolean local;

    public EditorialMedia(MediaService media, MediaStorage storage, JdbcTemplate jdbc, AuditService audit, Clock clock,
                         TransactionTemplate tx, CreateSnapshot snapshots, ObjectMapper json, Environment env, @Value("${app.media.public-origin:}") String origin) {
        this.media=media; this.storage=storage; this.jdbc=jdbc; this.audit=audit; this.clock=clock; this.tx=tx;
        this.snapshots=snapshots; this.json=json; this.origin=origin; this.local=env.acceptsProfiles(Profiles.of("local")) && !env.acceptsProfiles(Profiles.of("prod"));
    }

    public record Decision(@NotNull @Pattern(regexp="approve|reject") String decision,
                           @NotBlank @Size(max=500) String reason,@NotNull @Positive Integer version) {}
    public record Retry(@NotBlank @Size(max=500) String reason) {}
    public record Trial(int seconds,String state,int attempts,String errorCode) {}
    public record Processing(String assetId, String status, String reviewStatus, String jobState, int attempts, String errorCode,List<Trial> trials) {}
    private static final String PROCESSING = """
        SELECT a.id,a.status,a.review_status,j.state,j.attempts,COALESCE(a.failure_code,j.last_error) AS error_code
        FROM media_asset a LEFT JOIN media_processing_job j ON j.asset_id=a.id
        """;
    private Processing processingRow(java.sql.ResultSet r,int n) throws java.sql.SQLException {
        String asset=r.getString("id");
        var trials=jdbc.query("SELECT seconds,state,attempts,error_code FROM lesson_trial WHERE asset_id=? ORDER BY seconds",(row,i)->new Trial(row.getInt(1),row.getString(2),row.getInt(3),row.getString(4)),asset);
        return new Processing(
        r.getString("id"),r.getString("status"),r.getString("review_status"),r.getString("state"),r.getInt("attempts"),r.getString("error_code"),trials);
    }

    @GetMapping("/api/v1/admin/assets/{id}/processing")
    public ApiResponse<Processing> processing(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id) {
        media.get(admin,id);
        return ApiResponse.ok(jdbc.queryForObject(PROCESSING+" WHERE a.id=?",this::processingRow,id));
    }
    @GetMapping("/api/v1/admin/assets/processing-failures")
    @PreAuthorize("hasAuthority('content:read')")
    public ApiResponse<Page<Processing>> failures(@AuthenticationPrincipal AdminPrincipal admin,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        media.requireEditorialScope(admin);
        String where=" WHERE a.owner_admin_id IS NOT NULL AND (j.state='failed' OR a.status='rejected' OR EXISTS(SELECT 1 FROM lesson_trial tr WHERE tr.asset_id=a.id AND tr.state='failed'))";
        var args=new java.util.ArrayList<Object>();
        if (!"all".equals(admin.dataScope())) { where+=" AND a.owner_admin_id=?"; args.add(admin.id()); }
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM media_asset a LEFT JOIN media_processing_job j ON j.asset_id=a.id"+where,Long.class,args.toArray());
        args.add(pageSize); args.add(((long)page-1)*pageSize);
        return ApiResponse.ok(new Page<>(jdbc.query(PROCESSING+where+" ORDER BY a.updated_at DESC,a.id LIMIT ? OFFSET ?",this::processingRow,args.toArray()),page,pageSize,total));
    }

    @PostMapping("/api/v1/admin/assets/{id}/processing/retry")
    @PreAuthorize("hasAuthority('content:write')")
    public ApiResponse<JsonNode> retry(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Retry body) {
        media.get(admin,id);
        return tx.execute(s->ApiResponse.ok(snapshots.admin(admin.id(),"POST /admin/assets/"+id+"/processing/retry",key,body,
            oldId->media.get(admin,oldId),()->{
                var asset=media.lockEditorial(admin,id);
                var now=Timestamp.from(clock.instant());
                int changed=0;
                if ("processing".equals(asset.status()))
                    changed=jdbc.update("UPDATE media_processing_job SET state='pending',attempts=0,next_attempt_at=?,updated_at=? WHERE asset_id=? AND state='failed'",now,now,id);
                if ("ready".equals(asset.status()))
                    changed+=jdbc.update("UPDATE lesson_trial SET state='pending',attempts=0,next_attempt_at=?,error_code=NULL WHERE asset_id=? AND state='failed'",now,id);
                if (changed==0) throw MediaErrors.conflict("NO_FAILED_JOB","没有可重新执行的失败任务");
                audit.record(admin.id(),"media.processing.retry",id,body.reason());
                return json.valueToTree(asset);
            })));
    }

    @PutMapping("/api/v1/admin/assets/{id}/review")
    @PreAuthorize("hasAuthority('moderation:review')")
    public ApiResponse<Processing> review(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@Valid @RequestBody Decision decision) {
        if (!"all".equals(admin.dataScope())) throw BusinessException.forbidden();
        var asset=media.get(admin,id);
        if (asset.version()!=decision.version()) throw MediaErrors.conflict("VERSION_CONFLICT","媒资已变更");
        if (!"ready".equals(asset.status()) || !Set.of("avatar","cover","course_media").contains(asset.purpose()))
            throw MediaErrors.conflict("ASSET_NOT_READY","仅可审核技术校验已通过的编辑素材");
        String state="approve".equals(decision.decision()) ? "approved" : "rejected";
        boolean publish=state.equals("approved") && !asset.purpose().equals("course_media");
        String url=publish ? imageUrl(id) : null;
        byte[] bytes=publish ? render(asset) : null;
        return tx.execute(s->{
            int changed=jdbc.update("UPDATE media_asset SET review_status=?,public_url=?,version=version+1,updated_at=? WHERE id=? AND version=? AND status='ready' AND owner_admin_id IS NOT NULL",
                state,url,Timestamp.from(clock.instant()),id,decision.version());
            if (changed!=1) throw MediaErrors.conflict("VERSION_CONFLICT","媒资已变更");
            jdbc.update("DELETE FROM editorial_image WHERE asset_id=?",id);
            if (publish) jdbc.update("INSERT INTO editorial_image(asset_id,image_bytes) VALUES (?,?)",id,bytes);
            audit.record(admin.id(),"media.review."+state,id,decision.reason());
            return processing(admin,id);
        });
    }

    private String imageUrl(String id) {
        URI uri;
        try { uri=URI.create(origin); } catch (IllegalArgumentException e) { throw MediaErrors.unavailable(); }
        boolean loopback=local && "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost());
        if ((!loopback && !"https".equals(uri.getScheme())) || uri.getHost()==null || uri.getRawUserInfo()!=null ||
            !uri.getRawPath().isEmpty() || uri.getRawQuery()!=null || uri.getRawFragment()!=null) throw MediaErrors.unavailable();
        return origin+"/media/images/"+id;
    }
    private byte[] render(MediaModels.Asset asset) {
        Path source=null;
        try {
            source=Files.createTempFile("cxj-editorial-",".image");
            storage.download("validated/"+asset.id()+"/source",source,asset.sizeBytes(),asset.mimeType());
            BufferedImage image=ImageIO.read(source.toFile());
            if (image==null) throw MediaErrors.unavailable();
            double scale=Math.min(1.0,1280.0/Math.max(image.getWidth(),image.getHeight()));
            var output=new BufferedImage(Math.max(1,(int)(image.getWidth()*scale)),Math.max(1,(int)(image.getHeight()*scale)),BufferedImage.TYPE_INT_RGB);
            Graphics2D g=output.createGraphics();
            try {
                g.setColor(Color.WHITE); g.fillRect(0,0,output.getWidth(),output.getHeight());
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image,0,0,output.getWidth(),output.getHeight(),null);
            } finally { g.dispose(); }
            var bytes=new ByteArrayOutputStream();
            if (!ImageIO.write(output,"jpeg",bytes)) throw MediaErrors.unavailable();
            return bytes.toByteArray();
        } catch (IOException | MediaErrors.Invalid ex) { throw MediaErrors.unavailable(); }
        finally { if (source!=null) try { Files.deleteIfExists(source); } catch(IOException ex) { source.toFile().deleteOnExit(); } }
    }

    @GetMapping("/media/images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable String id) {
        var bytes=jdbc.query("""
            SELECT i.image_bytes FROM editorial_image i JOIN media_asset a ON a.id=i.asset_id
            WHERE a.id=? AND a.owner_admin_id IS NOT NULL AND a.purpose IN ('avatar','cover')
              AND a.review_status='approved' AND a.status='ready' AND a.public_url IS NOT NULL
            """,(r,n)->r.getBytes(1),id).stream().findFirst().orElseThrow(BusinessException::notFound);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_JPEG).body(bytes);
    }
}
