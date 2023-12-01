package com.changyang.ldap;

import java.io.Serializable;
import javax.naming.Name;
import lombok.Data;
import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;

/**
 * 用户条目
 *
 * @author wangsg
 **/
@Data
@Entry(objectClasses = {"top", "person", "organizationalPerson", "inetOrgPerson"})
public final class User implements Serializable {

    @Id
    private Name dn;

    @Attribute(name = "cn")
    private String name;

    @Attribute(name = "sn")
    private String surname;

    @Attribute(name = "uid")
    private String uid;

    @Attribute(name = "mail")
    private String email;

    @Attribute(name = "employeeNumber")
    private String jobNumber;

    @Attribute(name = "mobile")
    private String mobile;

    @Attribute(name = "description")
    private String remark;

    @Attribute(name = "title")
    private String title;

    @Attribute(name = "userPassword")
    private String password;
}
