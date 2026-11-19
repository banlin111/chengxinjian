package com.chengxinjian;

import com.chengxinjian.user.UserSessionService;
import com.chengxinjian.media.MediaStorage;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:practice;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USERNAME:sa}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}", "app.auth.bootstrap-username=practice-fixture","app.auth.bootstrap-password=practice-integration-only",
    "app.auth.local-captcha-ticket=practice-integration-ticket","app.practice.checkin-seconds=12", "app.practice.rule-version=test-only"})
@AutoConfigureMockMvc @ActiveProfiles("local")
class PracticeIntegrationTest {
    static final String BASE = "/api/v1/practice/sessions";
    static final String HISTORY = "/api/v1/me/practice/sessions";
    static final String STATS = "/api/v1/me/practice/stats";
    static final Instant START = Instant.parse("2026-11-19T15:59:50Z");
    static class TestClock extends Clock {
        volatile Instant now = START;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary TestClock practiceClock() { return new TestClock(); }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserSessionService sessions;
    @Autowired TestClock clock;
    @MockitoBean MediaStorage storage;
    final List<String> fixtureAssets=new ArrayList<>(), fixtureCategories=new ArrayList<>(), fixtureAudio=new ArrayList<>();
    String owner, token, other, otherToken;

    String uuid() { return UUID.randomUUID().toString(); }
    void time(int seconds) { clock.now = START.plusSeconds(seconds); }
    String user(String id) {
        var at = Timestamp.from(START);
        jdbc.update("INSERT INTO app_user(id,nickname,created_at,updated_at,last_login_at) VALUES (?,'练习测试',?,?,?)",
            id, at, at, at);
        return sessions.create(id).accessToken();
    }
    @BeforeEach void setup() {
        time(0);
        owner = uuid();
        other = uuid();
        when(storage.preview(anyString(),any())).thenReturn("https://fixture.example/audio");
        token = user(owner);
        otherToken = user(other);
    }
    @AfterEach void cleanup() {
        for (String table : List.of("practice_event_request","practice_event"))
            jdbc.update("DELETE FROM " + table + " WHERE session_id IN (SELECT id FROM practice_session WHERE user_id IN (?,?))", owner, other);
        for (String table:List.of("practice_played_interval","playback_ticket_snapshot","lesson_playback_event"))
            jdbc.update("DELETE FROM "+table+" WHERE playback_id IN (SELECT id FROM lesson_playback WHERE user_id IN (?,?))",owner,other);
        jdbc.update("DELETE FROM lesson_playback WHERE user_id IN (?,?)",owner,other);
        jdbc.update("DELETE FROM practice_audio_track WHERE session_id IN (SELECT id FROM practice_session WHERE user_id IN (?,?))",owner,other);
        jdbc.update("DELETE FROM mix_preset WHERE owner_user_id IN (?,?)",owner,other);
        for (String id:fixtureAudio) jdbc.update("DELETE FROM practice_audio WHERE id=?",id);
        for (String id:fixtureAssets) jdbc.update("DELETE FROM media_asset WHERE id=?",id);
        for (String id:fixtureCategories) jdbc.update("DELETE FROM content_category WHERE id=?",id);
        for (String table : List.of("practice_checkin_request","practice_checkin","practice_credit","practice_session","user_auth_session"))
            jdbc.update("DELETE FROM " + table + " WHERE user_id IN (?,?)", owner, other);
        jdbc.update("DELETE FROM app_user WHERE id IN (?,?)", owner, other);
    }

    JsonNode http(MockHttpServletRequestBuilder request, String bearer, Object body, int expected) throws Exception {
        if (bearer != null) request.header("Authorization","Bearer " + bearer);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return json.readTree(mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray());
    }
    Map<String,Object> timer(int plan) { return Map.of("kind","timer","plannedSeconds",plan,"tracks",List.of()); }
    JsonNode write(String route, String key, Object body, int expected) throws Exception {
        var envelope = http(post(route).header("Idempotency-Key",key), token, body, expected);
        return expected == 200 ? envelope.path("data") : envelope.path("code");
    }
    String start(int plan) throws Exception { return write(BASE,uuid(),timer(plan),200).get("id").asText(); }
    String events(String id) { return BASE + "/" + id + "/events"; }
    Map<String,Object> event(int sequence, String action, int delta, int at) {
        return Map.of("eventId",uuid(),"sequence",sequence,"action",action,"activeDeltaSeconds",delta,"occurredAt",START.plusSeconds(at).toString());
    }
    JsonNode send(String id,int seq,String action,int delta,int at) throws Exception {
        return write(events(id),uuid(),event(seq,action,delta,at),200);
    }
    JsonNode stats(String bearer) throws Exception {
        return http(get(STATS).param("from","2026-11-19").param("to","2026-11-21"),bearer,null,200).path("data");
    }

    @Test void recoversAcrossLoginAndAttributesSecondsAndCompletionToShanghaiDays() throws Exception {
        String id = start(100);
        time(5);
        send(id,1,"pause",500,5);
        time(8);
        token = sessions.create(owner).accessToken();
        var paused = http(get(BASE+"/"+id),token,null,200).path("data");
        assertThat(paused.path("status").asText()).isEqualTo("paused");
        assertThat(paused.path("version").asInt()).isEqualTo(2);
        send(id,2,"resume",100,8);
        time(23);
        var done = send(id,3,"finish",999,23);
        assertThat(done.path("creditedSeconds").asInt()).isEqualTo(20);
        assertThat(done.path("checkinEligible").asBoolean()).isTrue();
        var result = stats(token);
        assertThat(result.path("daily").findValues("seconds").stream().map(JsonNode::asInt).toList()).containsExactly(7,13,0);
        assertThat(result.path("totalSeconds").asInt()).isEqualTo(20);
        assertThat(result.path("completedSessions").asInt()).isEqualTo(1);
        assertThat(result.path("checkinDays").asInt()).isZero();
        assertThat(result.path("streakDays").asInt()).isZero();
        assertThat(http(get(STATS).param("from","2026-11-19").param("to","2026-11-19"),token,null,200)
            .at("/data/completedSessions").asInt()).isZero();
        assertThat(http(get(HISTORY).param("from","2026-11-20"),token,null,200).at("/data/total").asInt()).isZero();
        assertThat(http(get(HISTORY).param("to","2026-11-19"),token,null,200).at("/data/items/0")).isEqualTo(done);
        assertThat(stats(otherToken).path("totalSeconds").asInt()).isZero();
    }

    @Test void replaysHistoricalResponsesAndRejectsCollisionsEvenAfterFinish() throws Exception {
        String createKey=uuid();
        var initial=write(BASE,createKey,timer(30),200);
        String id=initial.get("id").asText(), key=uuid(), alias=uuid();
        time(6);
        var input=event(1,"heartbeat",6,6);
        var original=write(events(id),key,input,200);
        time(10);
        var finish=event(2,"finish",4,10);
        var done=write(events(id),uuid(),finish,200);
        assertThat(write(events(id),uuid(),finish,200)).isEqualTo(done);
        assertThat(write(events(id),alias,input,200)).isEqualTo(original);
        assertThat(write(events(id),alias,event(3,"heartbeat",1,10),409).asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(write(BASE,createKey,timer(30),200)).isEqualTo(initial);
        assertThat(write(BASE,createKey,timer(31),409).asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        var collision=new HashMap<>(input);
        collision.put("sequence",3);
        assertThat(write(events(id),uuid(),collision,409).asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(write(events(id),uuid(),event(1,"pause",6,6),409).asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(write(events(id),uuid(),event(3,"heartbeat",1,10),409).asText()).isEqualTo("PRACTICE_STATE_CONFLICT");
    }

    @Test void limitsOfflineBackfillByOccurrenceServerWindowAndPlan() throws Exception {
        String id=start(70);
        time(400);
        assertThat(send(id,1,"heartbeat",999,80).path("creditedSeconds").asInt()).isZero();
        assertThat(send(id,2,"heartbeat",999,105).path("creditedSeconds").asInt()).isEqualTo(5);
        assertThat(send(id,3,"heartbeat",999,380).path("creditedSeconds").asInt()).isEqualTo(65);
        write(events(id),uuid(),event(4,"pause",1,379),400);
        write(events(id),uuid(),event(4,"pause",1,480),400);
        assertThat(send(id,4,"pause",1000,401).path("creditedSeconds").asInt()).isEqualTo(70);
        time(420);
        var abandoned=send(id,5,"abandon",999,420);
        assertThat(abandoned.path("status").asText()).isEqualTo("abandoned");
        assertThat(abandoned.path("creditedSeconds").asInt()).isEqualTo(70);
        assertThat(stats(token).path("completedSessions").asInt()).isZero();
    }

    List<JsonNode> race(List<Callable<JsonNode>> requests) throws Exception {
        var ready=new CountDownLatch(requests.size());
        var start=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(requests.size())) {
            var futures=new ArrayList<Future<JsonNode>>();
            for (var call : requests) futures.add(pool.submit(() -> {
                ready.countDown(); start.await(); return call.call();
            }));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results=new ArrayList<JsonNode>();
            for (var f : futures) results.add(f.get(15,TimeUnit.SECONDS));
            return results;
        }
    }

    @Test void concurrentDevicesAndRetransmissionAllocateEachSecondOnce() throws Exception {
        String createKey=uuid();
        var starts=race(List.of(() -> write(BASE,createKey,timer(90),200),() -> write(BASE,createKey,timer(90),200)));
        assertThat(starts.getFirst()).isEqualTo(starts.getLast());
        String first=starts.getFirst().get("id").asText(), second=start(90);
        var a=event(1,"heartbeat",20,20);
        time(20);
        var results=race(List.of(() -> write(events(first),uuid(),a,200),() -> write(events(first),uuid(),a,200),
            () -> send(second,1,"heartbeat",20,20)));
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(results.get(0).path("creditedSeconds").asInt()+results.get(2).path("creditedSeconds").asInt()).isEqualTo(20);
        assertThat(stats(token).path("daily").findValues("seconds").stream().map(JsonNode::asInt).toList()).containsExactly(10,10,0);
    }

    @Test void lateDeviceFillsOnlyUnclaimedGapsBetweenEarlierIntervals() throws Exception {
        String a=start(90), b=start(90);
        time(45);
        send(a,1,"pause",5,10);
        send(a,2,"resume",0,20);
        send(a,3,"finish",5,25);
        assertThat(send(b,1,"finish",30,30).path("creditedSeconds").asInt()).isEqualTo(20);
        assertThat(stats(token).path("totalSeconds").asInt()).isEqualTo(30);
    }

    @Test void enforcesOwnerStateOrderedDeliveryAndSupportedRequests() throws Exception {
        String id=start(50);
        http(get(BASE+"/"+id),null,null,401);
        http(get(BASE+"/"+id),otherToken,null,404);
        http(post(events(id)).header("Idempotency-Key",uuid()),otherToken,event(1,"finish",0,0),404);
        http(get(HISTORY).param("pageSize","101"),token,null,400);
        http(get(STATS).param("from","2026-01-01").param("to","2027-01-02"),token,null,400);
        http(get(STATS).param("from","2026-01-02").param("to","2026-01-01"),token,null,400);
        write(BASE,"bad",timer(1),400);
        write(BASE,uuid(),timer(0),400);
        for (String kind : List.of("noise","guided")) {
            var body=new HashMap<>(timer(5));body.put("kind",kind);
            assertThat(write(BASE,uuid(),body,400).asText()).isEqualTo("VALIDATION_FAILED");
        }
        var bell=new HashMap<>(timer(5));bell.put("endBellAssetId",uuid());
        write(BASE,uuid(),bell,503);
        write(events(id),uuid(),event(1,"resume",0,0),409);
        write(events(id),uuid(),event(2,"finish",0,0),409);
        send(id,1,"pause",0,0);
        write(events(id),uuid(),event(2,"heartbeat",3,0),409);
        send(id,2,"finish",0,0);
        jdbc.update("UPDATE app_user SET status='banned' WHERE id=?", owner);
        http(get(BASE+"/"+id),token,null,403);
    }

    @Test void explicitCheckinRacesAndCrossDayRetryKeepOneFactAndNoRewards() throws Exception {
        time(10);
        String id = start(30), key = uuid();
        var body = Map.of("practiceSessionId", id);
        String path = "/api/v1/checkins";
        assertThat(write(path,key,body,409).asText()).isEqualTo("CHECKIN_NOT_ELIGIBLE");
        http(post(path).header("Idempotency-Key",uuid()),otherToken,body,404);
        time(23);
        send(id,1,"finish",13,23);
        var pair = race(List.of(() -> write(path,key,body,200), () -> write(path,uuid(),body,200)));
        assertThat(pair.stream().map(r -> r.path("alreadyCheckedIn").asBoolean()).toList())
            .containsExactlyInAnyOrder(false,true);
        for (var checkin : pair) {
            assertThat(checkin.path("creditedSeconds").asInt()).isEqualTo(13);
            assertThat(checkin.path("rewards").size()).isZero();
            assertThat(checkin.path("date").asText()).isEqualTo("2026-11-20");
        }
        assertThat(write(path,key,Map.of("practiceSessionId",start(5)),409).asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(stats(token).path("checkinDays").asInt()).isEqualTo(1);
        assertThat(stats(token).at("/daily/1/checkedIn").asBoolean()).isTrue();
        assertThat(http(get("/api/v1/home"),token,null,200).at("/data/checkedInToday").asBoolean()).isTrue();
        time(86423);
        token = sessions.create(owner).accessToken();
        assertThat(write(path,key,body,200)).isEqualTo(pair.getFirst());
        var calendar = http(get("/api/v1/me/checkins").param("month","2026-11"),token,null,200).path("data");
        assertThat(calendar.path("dates").toString()).isEqualTo("[\"2026-11-20\"]");
        assertThat(calendar.path("checkedInToday").asBoolean()).isFalse();
        assertThat(calendar.path("streakDays").asInt()).isEqualTo(1);
        String next = start(60);
        time(86443);
        send(next,1,"finish",20,86443);
        assertThat(write(path,uuid(),Map.of("practiceSessionId",next),200).path("streakDays").asInt()).isEqualTo(2);
        assertThat(stats(token).path("checkinDays").asInt()).isEqualTo(2);
        time(3*86400+50);
        token = sessions.create(owner).accessToken();
        assertThat(http(get("/api/v1/home"),token,null,200).at("/data/streakDays").asInt()).isZero();
        String later = start(60);
        time(3*86400+70);
        send(later,1,"finish",20,3*86400+70);
        assertThat(write(path,uuid(),Map.of("practiceSessionId",later),200).path("streakDays").asInt()).isEqualTo(1);
        assertThat(stats(token).path("streakDays").asInt()).isEqualTo(2);
    }

    String audioFixture(String kind) {
        String category=uuid(),id=uuid(),cover=uuid(),file=uuid();
        var at=Timestamp.from(START);
        jdbc.update("INSERT INTO content_category(id,name,kind,sort_order,status,version,created_at,updated_at) VALUES (?,?,?,0,'published',1,?,?)",
            category,category,kind,at,at);
        fixtureCategories.add(category);
        for (String asset:List.of(cover,file)) {
            boolean image=asset.equals(cover);
            jdbc.update("""
                INSERT INTO media_asset(id,purpose,filename,mime_type,size_bytes,duration_seconds,sha256,object_key,status,review_status,owner_admin_id,public_url,created_at,updated_at)
                VALUES (?,?,?, ?,100,60,?,?,'ready','approved',(SELECT id FROM admin_account WHERE username='practice-fixture'),?,?,?)
                """,asset,image?"cover":kind+"_media","fixture",image?"image/png":"audio/wav","1".repeat(64),"validated/"+asset+"/source",
                image?"https://fixture.example/cover":null,at,at);
            fixtureAssets.add(asset);
        }
        jdbc.update("""
            INSERT INTO practice_audio(id,kind,title,summary,category_id,cover_asset_id,asset_id,access_policy,status,version,created_at,updated_at)
            VALUES (?,?,?,'',?,?,?,'free','published',1,?,?)
            """,id,kind,kind,category,cover,file,at,at);
        fixtureAudio.add(id);return id;
    }
    String bound(String kind,String target,String session) throws Exception {
        return write("/api/v1/playbacks",uuid(),Map.of("target",Map.of("kind",kind,"id",target),
            "platform","wechat_ios","practiceSessionId",session),200).path("playbackId").asText();
    }
    Map<String,Object> played(int seq,int pos,int sec,int at) {
        return Map.of("eventId",uuid(),"sequence",seq,"positionSeconds",pos,"playedDeltaSeconds",sec,
            "speed",1,"kind","playing","occurredAt",START.plusSeconds(at).toString());
    }
    @Test void overlappingNoiseAndDevicesSettleOnceInEitherDeliveryOrder() throws Exception {
        var one=audioFixture("noise");var two=audioFixture("noise");
        var tracks=List.of(Map.of("noiseId",one,"volume",0.5),Map.of("noiseId",two,"volume",1));
        var session=write(BASE,uuid(),Map.of("kind","noise","plannedSeconds",60,"tracks",tracks),200).path("id").asText();
        var x=bound("noise",one,session);var y=bound("noise",two,session);
        String timer=start(60);
        time(15);
        // The first stream alone (in this case the practice heartbeat) has no confirmed media seconds.
        assertThat(send(session,1,"heartbeat",15,15).path("creditedSeconds").asInt()).isZero();
        var input=played(1,15,15,15);
        String alias=uuid();
        var accepted=race(List.of(()->write("/api/v1/playbacks/"+x+"/events",uuid(),input,200),
            ()->write("/api/v1/playbacks/"+y+"/events",uuid(),input,200)));
        write("/api/v1/playbacks/"+x+"/events",alias,input,200);
        write("/api/v1/playbacks/"+x+"/events",alias,played(2,15,0,15),409);
        assertThat(accepted.getFirst().path("creditedSeconds").asInt()).isEqualTo(15);
        write("/api/v1/playbacks/"+x+"/events",uuid(),input,200);
        assertThat(send(timer,1,"finish",15,15).path("creditedSeconds").asInt()).isZero();
        assertThat(http(get(BASE+"/"+session),token,null,200).at("/data/creditedSeconds").asInt()).isEqualTo(15);
        time(20);
        write("/api/v1/playbacks/"+x+"/events",uuid(),played(2,20,5,20),200);
        assertThat(send(session,2,"finish",5,20).path("creditedSeconds").asInt()).isEqualTo(20);
        assertThat(stats(token).path("daily").findValues("seconds").stream().map(JsonNode::asInt).toList()).containsExactly(10,10,0);
        assertThat(stats(token).path("totalSeconds").asInt()).isEqualTo(20);
    }

    @Test void personalMixRestoresAndFreezesOneRevisionAcrossEditsAndRetries() throws Exception {
        String route="/api/v1/me/mix-presets", a=audioFixture("noise"), b=audioFixture("noise");
        var tracks=List.of(Map.of("noiseId",b,"volume",0.35),Map.of("noiseId",a,"volume",0.7));
        var input=Map.of("title","雨夜","tracks",tracks);
        String key=uuid();
        var receipts=race(List.of(()->write(route,key,input,200),()->write(route,key,input,200)));
        var saved=receipts.getFirst();
        String id=saved.path("id").asText(), detail=route+"/"+id;
        assertThat(saved).isEqualTo(receipts.getLast());
        assertThat(saved.has("status")).isFalse();
        assertThat(http(get(detail),sessions.create(owner).accessToken(),null,200).path("data")).isEqualTo(saved);
        assertThat(http(get(route),otherToken,null,200).at("/data/total").asInt()).isZero();
        http(get(detail),otherToken,null,404);
        http(get(detail),null,null,401);
        var start=Map.of("kind","noise","plannedSeconds",60,"mixPresetId",id,"tracks",List.of());
        http(post(BASE).header("Idempotency-Key",uuid()),otherToken,start,404);
        String startKey=uuid();
        var session=write(BASE,startKey,start,200);
        String sid=session.path("id").asText();
        assertThat(session.path("tracks")).isEqualTo(saved.path("tracks"));
        var changes=Map.of("title","只听细雨","tracks",List.of(tracks.getLast()),"version",1);
        http(put(detail),otherToken,changes,404);
        http(put(detail),token,changes,200);
        http(put(detail),token,changes,409);
        write(route,key,Map.of("title","不同","tracks",tracks),409);
        assertThat(write(route,key,input,200)).isEqualTo(saved);
        assertThat(write(BASE,startKey,start,200)).isEqualTo(session);
        var stale=new HashMap<String,Object>(start);stale.put("tracks",tracks);
        write(BASE,uuid(),stale,409);
        var newest=write(BASE,uuid(),start,200);
        assertThat(newest.path("mixPresetVersion").asInt()).isEqualTo(2);
        assertThat(newest.path("tracks").size()).isEqualTo(1);
        http(delete(detail),otherToken,Map.of("version",2),404);
        http(delete(detail),token,Map.of("version",1),409);
        http(delete(detail),token,Map.of("version",2),200);
        http(delete(detail),token,Map.of("version",2),200);
        http(get(detail),token,null,404);
        write(BASE,uuid(),start,404);
        var first=bound("noise",a,sid);var second=bound("noise",b,sid);
        time(15);
        race(List.of(()->write("/api/v1/playbacks/"+first+"/events",uuid(),played(1,15,15,15),200),
                     ()->write("/api/v1/playbacks/"+second+"/events",uuid(),played(1,15,15,15),200)));
        var done=send(sid,1,"finish",15,15);
        assertThat(done.path("mixPresetVersion").asInt()).isEqualTo(1);
        assertThat(done.path("creditedSeconds").asInt()).isEqualTo(15);
        assertThat(stats(token).path("daily").findValues("seconds").stream().map(JsonNode::asInt).toList()).containsExactly(10,5,0);
    }

    @Test void fiveTrackMixRemainsCoherentWhenPracticeRacesAnEdit() throws Exception {
        String route="/api/v1/me/mix-presets";
        var tracks=new ArrayList<Map<String,Object>>();
        for (int i=0;i<5;i++) tracks.add(Map.of("noiseId",audioFixture("noise"),"volume",i/4.0));
        var saved=write(route,uuid(),Map.of("title","五轨","tracks",tracks),200);
        var excess=new ArrayList<>(tracks);excess.add(Map.of("noiseId",audioFixture("noise"),"volume",1));
        write(route,uuid(),Map.of("title","过多","tracks",excess),400);
        String id=saved.path("id").asText();
        var reverse=tracks.reversed();
        var results=race(List.of(
            ()->write(BASE,uuid(),Map.of("kind","noise","plannedSeconds",90,"tracks",List.of(),"mixPresetId",id),200),
            ()->http(put(route+"/"+id),token,Map.of("title","倒序","version",1,"tracks",reverse),200).path("data")));
        var session=results.getFirst();
        int version=session.path("mixPresetVersion").asInt();
        assertThat(version).isIn(1,2);
        assertThat(session.path("tracks")).isEqualTo(version==1?saved.path("tracks"):results.getLast().path("tracks"));
        assertThat(session.path("tracks").size()).isEqualTo(5);
    }

    @Test void privateMixPreservesMuteAndChecksCurrentNoiseAccessAtStart() throws Exception {
        String route="/api/v1/me/mix-presets", audible=audioFixture("noise"), muted=audioFixture("noise");
        var silence=Map.of("noiseId",muted,"volume",0);
        for (var bad : List.of(List.of(),List.of(silence,silence),List.of(Map.of("noiseId",muted,"volume",1.1))))
            write(route,uuid(),Map.of("title","配方","tracks",bad),400);
        write(route,uuid(),Map.of("title","配方","tracks",List.of(Map.of("noiseId",audioFixture("meditation"),"volume",1))),404);
        var tracks=List.of(silence,Map.of("noiseId",audible,"volume",1));
        var saved=write(route,uuid(),Map.of("title","配方","tracks",tracks),200);
        var start=Map.of("kind","noise","plannedSeconds",60,"mixPresetId",saved.path("id").asText(),"tracks",List.of());
        var session=write(BASE,uuid(),start,200).path("id").asText();
        var ticket=bound("noise",muted,session);
        time(5);
        write("/api/v1/playbacks/"+ticket+"/events",uuid(),played(1,5,5,5),200);
        assertThat(send(session,1,"heartbeat",5,5).path("creditedSeconds").asInt()).isZero();
        jdbc.update("UPDATE practice_audio SET access_policy='member' WHERE id=?",audible);
        write(BASE,uuid(),start,403);
        jdbc.update("UPDATE practice_audio SET status='archived' WHERE id=?",audible);
        write(BASE,uuid(),start,404);
        assertThat(http(get(route+"/"+saved.path("id").asText()),token,null,200).at("/data/tracks")).isEqualTo(saved.path("tracks"));
    }

    @Test void guidedPracticeNeedsNarrationAndCannotCreditSeekOrPausedTime() throws Exception {
        String guide=audioFixture("meditation"),noise=audioFixture("noise");
        var start=Map.of("kind","guided","meditationId",guide,"tracks",List.of(Map.of("noiseId",noise,"volume",0.2)),"plannedSeconds",50);
        String session=write(BASE,uuid(),start,200).path("id").asText();
        String main=bound("meditation",guide,session),ambient=bound("noise",noise,session);
        time(10);
        write("/api/v1/playbacks/"+ambient+"/events",uuid(),played(1,10,10,10),200);
        var seek=new HashMap<>(played(1,20,10,10));seek.put("kind","seek");
        write("/api/v1/playbacks/"+main+"/events",uuid(),seek,200);
        assertThat(send(session,1,"heartbeat",10,10).path("creditedSeconds").asInt()).isZero();
        time(18);
        var finish=event(2,"finish",8,18);
        assertThat(write(events(session),uuid(),finish,409).asText()).isEqualTo("PRACTICE_PLAYBACK_PENDING");
        send(session,2,"pause",8,18);
        time(24);
        write("/api/v1/playbacks/"+main+"/events",uuid(),played(2,28,8,18),200);
        send(session,3,"resume",0,24);
        time(30);
        var sped=new HashMap<>(played(3,40,12,30));sped.put("speed",2);
        write("/api/v1/playbacks/"+main+"/events",uuid(),sped,200);
        var done=send(session,4,"finish",20,30);
        assertThat(done.path("creditedSeconds").asInt()).isEqualTo(14);
        assertThat(done.path("meditationId").asText()).isEqualTo(guide);
        assertThat(stats(token).path("totalSeconds").asInt()).isEqualTo(14);
        http(post("/api/v1/playbacks/"+main+"/renew").header("Idempotency-Key",uuid()),token,null,409);
    }
}
