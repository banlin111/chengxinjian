package com.chengxinjian.catalog;

import com.chengxinjian.common.BusinessException;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MixPresets {
    public record Track(@NotBlank @Size(max=36) String noiseId,
                        @NotNull @DecimalMin("0") @DecimalMax("1") Double volume) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Preset(String id,int version,Instant createdAt,Instant updatedAt,String title,List<Track> tracks,
                         String status,Boolean backgroundAssetReady) {}

    private static final String VISIBLE_NOISE = "SELECT c.id " + PracticeAudioCatalog.FROM
        + " WHERE c.kind='noise' AND " + PracticeAudioCatalog.VISIBLE;
    private static final String PUBLIC = """
        p.owner_user_id IS NULL AND p.status='published' AND NOT EXISTS (
            SELECT 1 FROM mix_preset_track t WHERE t.preset_id=p.id AND t.noise_id NOT IN (
        """ + VISIBLE_NOISE + "))";
    private final JdbcTemplate jdbc;
    private final Clock clock;
    MixPresets(JdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }

    private List<Track> tracks(String id) {
        return jdbc.query("SELECT noise_id,volume FROM mix_preset_track WHERE preset_id=? ORDER BY track_no",
            (r,n)->new Track(r.getString(1),r.getDouble(2)),id);
    }
    private Preset preset(java.sql.ResultSet r,int n) throws java.sql.SQLException {
        boolean editorial=r.getString("owner_user_id")==null;
        return new Preset(r.getString("id"),r.getInt("version"),r.getTimestamp("created_at").toInstant(),
            r.getTimestamp("updated_at").toInstant(),r.getString("title"),tracks(r.getString("id")),
            editorial?r.getString("status"):null,editorial?Boolean.FALSE:null);
    }
    private Preset one(String predicate,boolean lock,Object... args) {
        return jdbc.query("SELECT p.* FROM mix_preset p WHERE "+predicate+(lock?" FOR UPDATE":""),this::preset,args)
            .stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    Preset owned(String user,String id,boolean lock) {
        return one("p.id=? AND p.owner_user_id=? AND p.status<>'archived'",lock,id,user);
    }
    Preset editorial(String id,boolean lock) {
        return one("p.id=? AND p.owner_user_id IS NULL",lock,id);
    }
    public Preset forPractice(String user,String id) {
        // Writers take the same parent lock before replacing tracks. Copy exactly one saved revision.
        return one("p.id=? AND ((p.owner_user_id=? AND p.status='draft') OR (p.owner_user_id IS NULL AND p.status='published'))",true,id,user);
    }

    CategoryModels.Page<Preset> listPublic(int page,int size) { return page(PUBLIC,List.of(),page,size); }
    CategoryModels.Page<Preset> listOwned(String user,int page,int size) {
        return page("p.owner_user_id=? AND p.status='draft'",List.of(user),page,size);
    }
    CategoryModels.Page<Preset> listAdmin(CourseModels.AdminFilters filters,int page,int size) {
        if (filters.categoryId()!=null || filters.teacherId()!=null || filters.courseId()!=null)
            throw BusinessException.invalid("配方不支持分类、讲师或课程筛选");
        String where="p.owner_user_id IS NULL";
        var args=new ArrayList<Object>();
        if (filters.q()!=null) {
            where+=" AND p.title LIKE ? ESCAPE '!'";
            args.add("%"+filters.q().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        }
        if (filters.status()!=null) {where+=" AND p.status=?";args.add(filters.status());}
        return page(where,args,page,size);
    }
    private CategoryModels.Page<Preset> page(String where,List<?> args,int page,int size) {
        String from=" FROM mix_preset p WHERE "+where;
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());
        var query=new ArrayList<Object>(args);
        query.add(size);query.add(((long)page-1)*size);
        return new CategoryModels.Page<>(jdbc.query("SELECT p.*"+from+" ORDER BY p.updated_at DESC,p.id LIMIT ? OFFSET ?",
            this::preset,query.toArray()),page,size,total);
    }

    static void version(Preset preset,Integer version) {
        if (version==null) throw BusinessException.invalid("请提供当前版本");
        if (version!=preset.version()) throw new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","配方版本已变更");
    }

    Preset save(String owner,Preset old,String title,String status,List<Track> tracks) {
        var ids=new HashSet<String>();
        // Draft editorial recipes may reference unpublished noise. Every published or personal selection must be visible.
        String allowed=owner!=null || "published".equals(status) ? VISIBLE_NOISE : "SELECT c.id FROM practice_audio c WHERE c.kind='noise'";
        for (Track track:tracks) {
            String storedId=jdbc.queryForList(allowed+" AND c.id=?",String.class,track.noiseId())
                .stream().findFirst().orElseThrow(BusinessException::notFound);
            if (!ids.add(storedId)) throw BusinessException.invalid("同一音轨不能重复");
        }
        var now=Timestamp.from(clock.instant());
        String id=old==null?UUID.randomUUID().toString():old.id();
        if (old==null) jdbc.update("INSERT INTO mix_preset(id,owner_user_id,title,status,version,created_at,updated_at) VALUES (?,?,?,?,1,?,?)",
            id,owner,title,status,now,now);
        else {
            jdbc.update("UPDATE mix_preset SET title=?,status=?,version=version+1,updated_at=? WHERE id=?",title,status,now,id);
            jdbc.update("DELETE FROM mix_preset_track WHERE preset_id=?",id);
        }
        for (int i=0;i<tracks.size();i++) {
            Track track=tracks.get(i);
            jdbc.update("INSERT INTO mix_preset_track(preset_id,track_no,noise_id,volume) VALUES (?,?,?,?)",
                id,i,track.noiseId(),track.volume());
        }
        return one("p.id=?",false,id);
    }
    void removeOwned(String user,String id,int expected) {
        var current=jdbc.queryForList("SELECT status,version FROM mix_preset WHERE id=? AND owner_user_id=? FOR UPDATE",id,user)
            .stream().findFirst().orElseThrow(BusinessException::notFound);
        if ("archived".equals(current.get("status"))) return;
        if (((Number)current.get("version")).intValue()!=expected)
            throw new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","配方版本已变更");
        archive(id);
    }
    void archive(String id) {
        jdbc.update("UPDATE mix_preset SET status='archived',version=version+1,updated_at=? WHERE id=?",Timestamp.from(clock.instant()),id);
    }
}
