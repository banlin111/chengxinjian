package com.chengxinjian.user;

import com.chengxinjian.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WechatPhoneGatewayTest {
    @Test void providerNumberMustBeMainlandAndCarryThisApplicationWatermark() {
        var appId="wx0123456789abcdef";
        var client=new WechatPhoneGateway(new WechatProperties(appId,""),new ObjectMapper(),Clock.systemUTC());
        var info=new ObjectMapper().createObjectNode().put("countryCode","86").put("purePhoneNumber","13800000001");
        assertThatThrownBy(()->client.verifiedNumber(info)).isInstanceOf(BusinessException.class);
        info.putObject("watermark").put("appid","another-app");
        assertThatThrownBy(()->client.verifiedNumber(info)).isInstanceOf(BusinessException.class);
        info.putObject("watermark").put("appid",appId);
        assertThat(client.verifiedNumber(info)).isEqualTo("13800000001");
        assertThatThrownBy(()->client.verifiedNumber(info.put("countryCode","852"))).isInstanceOf(BusinessException.class);
    }
}
