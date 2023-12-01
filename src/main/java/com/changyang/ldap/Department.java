package com.changyang.ldap;

import java.io.Serializable;
import javax.naming.Name;
import lombok.Data;
import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;

/**
 * 部门条目
 *
 * @author wangsg
 **/
@Data
@Entry(objectClasses = {"top", "organizationalUnit"})
public final class Department implements Serializable {

    @Id
    private Name dn;

    @Attribute(name = "ou")
    private String name;

    @Attribute(name = "description")
    private String description;
}
