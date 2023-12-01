package com.changyang.ldap;

import java.io.Serializable;
import lombok.NonNull;

/**
 * 钉钉应用凭证
 *
 * @author wangsg
 **/
public record AppCertificate(@NonNull String key, @NonNull String secret) implements Serializable {}
