package com.chengxinjian.auth;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Arrays;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Run via PropertiesLauncher. Does not start Spring, a web listener, workers, seeds or migrations. */
public final class AdminBootstrapCommand {
    record Input(String username,String displayName,String password,String reason) {
        @Override public String toString() { return "Input[redacted]"; }
    }
    public static void main(String[] args) {
        byte[] bytes=null;
        try {
            if (args.length!=1 || !"--initialize-first-admin".equals(args[0])) throw new IllegalArgumentException();
            String url=System.getenv("DB_URL"),user=System.getenv("DB_USERNAME"),secret=System.getenv("DB_PASSWORD");
            if (url==null || !url.startsWith("jdbc:mysql:") || user==null || user.isBlank() || secret==null || secret.isBlank()) throw new IllegalArgumentException();
            bytes=System.in.readNBytes(8193); if(bytes.length>8192) throw new IllegalArgumentException();
            var mapper=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            var input=mapper.readValue(bytes,Input.class);
            var source=new DriverManagerDataSource(url,user,secret); var jdbc=new JdbcTemplate(source);
            var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            tx.executeWithoutResult(status->AdminInitialization.initialize(jdbc,new BCryptPasswordEncoder(12),Clock.systemUTC(),
                input.username(),input.displayName(),input.password(),input.reason()));
            System.out.println("First administrator initialized and audited. Captcha remains required for login.");
        } catch (Exception ex) {
            System.err.println("Initialization refused. Check private input, MySQL connectivity, applied migrations and whether an administrator already exists. No credentials are printed.");
            System.exit(2);
        } finally { if(bytes!=null) Arrays.fill(bytes,(byte)0); }
    }
}
