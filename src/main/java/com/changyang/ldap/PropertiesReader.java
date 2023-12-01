package com.changyang.ldap;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ldap.core.support.LdapContextSource;

/**
 * @author wangsg
 **/
@Slf4j
public class PropertiesReader {

    private static final String DEFAULT_PASSWORD = "qwer1234";

    private final Properties properties;
    private AppCertificate dingTalkCertificate;
    private LdapContextSource ldapContextSource;

    public PropertiesReader(String filepath) {
        if (StringUtils.isBlank(filepath)) {
            throw new IllegalArgumentException("filepath of properties must not be blank");
        }
        properties = new Properties();
        log.debug("read properties from '{}'", filepath);
        try (InputStream inputStream = new FileInputStream(filepath)) {
            properties.load(inputStream);
        } catch (FileNotFoundException e) {
            log.error("cannot find the properties file");
            throw new RuntimeException(e);
        } catch (IOException e) {
            log.error("cannot read properties, error={}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public AppCertificate getDingTalkCertificate() {
        if (dingTalkCertificate == null) {
            String appKey = properties.getProperty("ding-talk.key");
            String appSecret = properties.getProperty("ding-talk.secret");
            dingTalkCertificate = new AppCertificate(appKey, appSecret);
        }
        return dingTalkCertificate;
    }

    public LdapContextSource getLdapContextSource() {
        if (ldapContextSource == null) {
            ldapContextSource = new LdapContextSource();
            ldapContextSource.setUrls(properties.getProperty("ldap.urls").split(","));
            ldapContextSource.setBase(properties.getProperty("ldap.base"));
            ldapContextSource.setUserDn(properties.getProperty("ldap.username"));
            ldapContextSource.setPassword(properties.getProperty("ldap.password"));
            ldapContextSource.afterPropertiesSet();
        }
        return ldapContextSource;
    }

    public String getDefaultPassword() {
        String password = properties.getProperty("default.password");
        return StringUtils.isBlank(password) ? DEFAULT_PASSWORD : password;
    }
}
