package com.chengxinjian.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CosMediaStorageTest {
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final MediaProperties properties=new MediaProperties("tencent","fixture-1234567890","ap-shanghai","permanent-id","permanent-key","",1000,1000,5,false);
    @Test void postPolicyUsesOfficialFieldsAndBindsSingleObjectMimeSizeAndExpiry() throws Exception {
        var storage=new CosMediaStorage(properties,json,Clock.systemUTC());var expires=Instant.parse("2026-11-10T10:10:00Z");
        var ticket=storage.signTicket("fixture","quarantine/fixture/source","image/png",123,expires,expires.minusSeconds(600),"temporary-id","temporary-secret","temporary-token");
        var fields=ticket.formData();assertThat(fields).containsKeys("key","q-sign-algorithm","q-ak","q-key-time","q-signature","x-cos-security-token","policy");
        assertThat(json.writeValueAsString(ticket)).doesNotContain("permanent-id","permanent-key","temporary-secret");
        var policy=json.readTree(Base64.getDecoder().decode(fields.get("policy")));
        assertThat(policy.get("expiration").asText()).isEqualTo(expires.toString());
        assertThat(policy.get("conditions").toString()).contains("quarantine/fixture/source","image/png","[\"content-length-range\",123,123]");
        assertThat(storage.resource("quarantine/fixture/source")).isEqualTo("qcs::cos:ap-shanghai:uid/1234567890:fixture-1234567890/quarantine/fixture/source");
        assertThat(properties.toString()).doesNotContain("permanent-id","permanent-key");assertThat(ticket.toString()).isEqualTo("Ticket[private]");
    }
    @Test void signingChangesWheneverRestrictedObjectSizeOrMimeChanges() throws Exception {
        var storage=new CosMediaStorage(properties,json,Clock.systemUTC());var start=Instant.parse("2026-11-10T10:00:00Z");var end=start.plusSeconds(600);
        var a=storage.signTicket("a","quarantine/a/source","image/png",10,end,start,"temporary-id","temporary-secret","token");
        var b=storage.signTicket("a","quarantine/b/source","image/png",10,end,start,"temporary-id","temporary-secret","token");
        var c=storage.signTicket("a","quarantine/a/source","image/png",11,end,start,"temporary-id","temporary-secret","token");
        var d=storage.signTicket("a","quarantine/a/source","image/jpeg",10,end,start,"temporary-id","temporary-secret","token");
        assertThat(Set.of(a.formData().get("q-signature"),b.formData().get("q-signature"),c.formData().get("q-signature"),d.formData().get("q-signature"))).hasSize(4);
    }
    @Test void missingConfigFailsClosedWithoutLeakingCredentials() {
        var storage=new CosMediaStorage(new MediaProperties("tencent","","","","","",1000,1000,5,false),json,Clock.systemUTC());
        assertThatThrownBy(()->storage.ticket("a","quarantine/a/source","image/png",10,Instant.now().plusSeconds(600)))
            .isInstanceOf(com.chengxinjian.common.BusinessException.class).hasMessage("媒资服务暂不可用，请稍后重试");
    }
}
