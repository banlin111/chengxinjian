package com.chengxinjian.user;

import com.chengxinjian.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WechatCode2SessionClientTest {
    private final WechatProperties config=new WechatProperties("wx0123456789abcdef","0123456789abcdef0123456789abcdef");
    private final ObjectMapper json=new ObjectMapper();
    @Test void successfulIdentityDiscardsSessionKeyAndUnionIdIsOptional() {
        var client=new WechatCode2SessionClient(config,json);
        var identity=client.parse("{\"openid\":\"open\",\"session_key\":\"secret-key\"}");
        assertThat(identity.openId()).isEqualTo("open"); assertThat(identity.unionId()).isNull();
        assertThat(identity.toString()).doesNotContain("open","secret-key");
        assertThat(config.toString()).doesNotContain(config.appSecret());
        assertThat(client.parse("{\"errcode\":0,\"openid\":\"open\",\"session_key\":\"key\",\"unionid\":\"union\"}").unionId()).isEqualTo("union");
    }
    @Test void invalidAndUsedCodesReturn400AndOtherErrorsDoNotLeakProviderMessages() {
        var client=new WechatCode2SessionClient(config,json);
        for (int code:new int[]{40029,40163,-1,45011,40013,40125,40226}) {
            int status=code==40029 || code==40163 ? 400 : code==40226 ? 403 : 503;
            assertThatThrownBy(()->client.parse("{\"errcode\":"+code+",\"errmsg\":\"sensitive-response\"}"))
                .isInstanceOfSatisfying(BusinessException.class,e->{assertThat(e.status().value()).isEqualTo(status);assertThat(e.getMessage()).doesNotContain("sensitive");});
        }
    }
    @Test void malformedAndPartialProviderResponsesFailClosed() {
        var client=new WechatCode2SessionClient(config,json);
        for (String body:new String[]{"not-json","{}","[]","{\"errcode\":\"0\"}","{\"openid\":42,\"session_key\":\"key\"}","{\"openid\":\"open\"}","{\"errcode\":0,\"errcode\":40029}"}) {
            assertThatThrownBy(()->client.parse(body)).isInstanceOf(BusinessException.class);
        }
    }
    @Test void missingConfigurationNeverMakesNetworkCall() {
        var http=mock(HttpClient.class);var client=new WechatCode2SessionClient(new WechatProperties("",""),json,http);
        assertThatThrownBy(()->client.exchange("code")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.status().value()).isEqualTo(503));
        verifyNoInteractions(http);
    }
    @Test @SuppressWarnings("unchecked") void realHttpAdapterUsesFixedHttpsEndpointAndBoundedTimeoutWithoutRedirects() throws Exception {
        var http=mock(HttpClient.class);var response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("{\"openid\":\"open\",\"session_key\":\"key\"}");
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(invocation->{
            HttpRequest request=invocation.getArgument(0);
            assertThat(request.uri().getScheme()).isEqualTo("https");assertThat(request.uri().getHost()).isEqualTo("api.weixin.qq.com");
            assertThat(request.uri().getRawQuery()).contains("js_code=a%26b","grant_type=authorization_code");
            assertThat(request.timeout().orElseThrow().toSeconds()).isEqualTo(5); return response;
        });
        assertThat(new WechatCode2SessionClient(config,json,http).exchange("a&b").openId()).isEqualTo("open");
    }
    @Test @SuppressWarnings("unchecked") void timeoutAndHttpFailuresAreSanitizedAndNotAutomaticallyRetried() throws Exception {
        var http=mock(HttpClient.class);
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("secret-in-uri"));
        assertThatThrownBy(()->new WechatCode2SessionClient(config,json,http).exchange("code"))
            .isInstanceOfSatisfying(BusinessException.class,e->{assertThat(e.status().value()).isEqualTo(503);assertThat(e.getMessage()).doesNotContain("secret-in-uri");assertThat(e.getCause()).isNull();});
        verify(http,times(1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
}
