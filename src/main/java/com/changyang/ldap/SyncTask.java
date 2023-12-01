package com.changyang.ldap;

import static com.changyang.ldap.DingTalkApi.ROOT_DEPARTMENT_ID;

import com.dingtalk.api.response.OapiV2DepartmentGetResponse.DeptGetResponse;
import com.dingtalk.api.response.OapiV2DepartmentListsubResponse.DeptBaseResponse;
import com.dingtalk.api.response.OapiV2UserListResponse.ListUserResponse;
import com.dingtalk.api.response.OapiV2UserListResponse.PageResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.naming.Name;
import javax.naming.ldap.LdapName;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.AbsoluteTrueFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.ldap.query.SearchScope;
import org.springframework.ldap.support.LdapNameBuilder;

/**
 * 同步任务
 *
 * @author wangsg
 **/
@Slf4j
public class SyncTask implements Runnable {

    private static final long DEFAULT_PAGE_SIZE = 100;

    private final DingTalkApi dingTalkApi;
    private final LdapTemplate template;
    private final String defaultPassword;

    public SyncTask(String filepath) {
        PropertiesReader reader = new PropertiesReader(filepath);
        this.dingTalkApi = new DingTalkApi(reader.getDingTalkCertificate());
        this.template = new LdapTemplate(reader.getLdapContextSource());
        this.defaultPassword = reader.getDefaultPassword();
    }

    @Override
    public void run() {
        DeptGetResponse company = dingTalkApi.getDepartmentDetail(ROOT_DEPARTMENT_ID);
        LdapName rootDn = LdapNameBuilder.newInstance().add("ou", company.getName()).build();
        LdapQuery rootQuery = LdapQueryBuilder.query().searchScope(SearchScope.ONELEVEL)
            .filter(new EqualsFilter("ou", company.getName()));
        List<Department> roots = template.find(rootQuery, Department.class);
        Department root;
        if (roots.isEmpty()) {
            root = new Department();
            root.setDn(rootDn);
            root.setName(company.getName());
            root.setDescription(company.getBrief());
            log.info("create a root department, dn={}", rootDn);
            template.create(root);
        } else if (roots.size() == 1) {
            root = roots.get(0);
        } else {
            log.error("more than one root departments were found, dn={}", rootDn);
            throw new AppException("more than one root departments were found");
        }

        List<DepartmentPair> pairs = updateDepartment(new DepartmentPair(ROOT_DEPARTMENT_ID, root));
        for (DepartmentPair pair : pairs) {
            updateUsers(pair);
        }
    }

    /**
     * 从钉钉同步部门架构至LDAP
     *
     * @param root 根部门
     * @return 新的部门列表
     */
    private List<DepartmentPair> updateDepartment(DepartmentPair root) {
        List<DepartmentPair> pairs = new ArrayList<>();
        pairs.add(root);
        int index = 0;
        while (index < pairs.size()) {
            DepartmentPair parent = pairs.get(index);
            Name parentDn = parent.department.getDn();
            LdapQuery query = LdapQueryBuilder.query().searchScope(SearchScope.ONELEVEL)
                .base(parentDn).filter(new AbsoluteTrueFilter());
            Map<String, Department> ldapDepartments = template.find(query, Department.class).stream()
                .collect(Collectors.toMap(Department::getName, t -> t));
            for (DeptBaseResponse dept : dingTalkApi.listSubDepartments(parent.deptId())) {
                String deptName = dept.getName();
                Department department = ldapDepartments.remove(deptName);
                if (department == null) {
                    department = new Department();
                    department.setDn(LdapNameBuilder.newInstance(parentDn).add("ou", deptName).build());
                    department.setName(deptName);
                    log.info("create a department, dn={}", department.getDn());
                    template.create(department);
                }
                pairs.add(new DepartmentPair(dept.getDeptId(), department));
            }
            Collection<Department> deletedDepartments = ldapDepartments.values();
            if (!deletedDepartments.isEmpty()) {
                for (Department deletedDepartment : deletedDepartments) {
                    log.info("delete a department, dn={}", deletedDepartment.getDn());
                    template.unbind(deletedDepartment.getDn(), true);
                }
            }
            index++;
        }
        return pairs;
    }

    /**
     * 从钉钉同步部门用户至LDAP（不包括子部门用户）
     */
    private void updateUsers(DepartmentPair pair) {
        Name deptDn = pair.department.getDn();
        LdapQuery query = LdapQueryBuilder.query().searchScope(SearchScope.ONELEVEL)
            .base(deptDn).filter(new AbsoluteTrueFilter());
        Map<String, User> ldapUsers = template.find(query, User.class).stream()
            .collect(Collectors.toMap(User::getName, t -> t));
        for (ListUserResponse userResponse : listDingUsersByDept(pair.deptId())) {
            String username = userResponse.getName();
            String email = userResponse.getEmail();
            if (StringUtils.isBlank(email)) {
                log.warn("no email is set for '{}'", username);
                continue;
            }
            User user = ldapUsers.remove(username);
            if (user == null) {
                user = new User();
                user.setDn(LdapNameBuilder.newInstance(deptDn).add("cn", username).build());
                user.setName(username);
                user.setSurname(username.substring(0, 1));
                user.setUid(email);
                user.setEmail(email);
                setNotBlankProperty(userResponse::getJobNumber, user::setJobNumber);
                setNotBlankProperty(userResponse::getMobile, user::setMobile);
                setNotBlankProperty(userResponse::getRemark, user::setRemark);
                setNotBlankProperty(userResponse::getTitle, user::setTitle);
                user.setPassword(defaultPassword);
                log.info("create a user, dn={}", user.getDn());
                template.create(user);
            }
        }
        Collection<User> deletedUsers = ldapUsers.values();
        if (!deletedUsers.isEmpty()) {
            for (User deletedUser : deletedUsers) {
                log.info("delete a user, dn={}", deletedUser.getDn());
                template.unbind(deletedUser.getDn(), false);
            }
        }
    }

    private void setNotBlankProperty(Supplier<String> getter, Consumer<String> setter) {
        String value = getter.get();
        if (StringUtils.isNotBlank(value)) {
            setter.accept(value);
        }
    }

    private List<ListUserResponse> listDingUsersByDept(long deptId) {
        List<ListUserResponse> users = new LinkedList<>();
        long start = 0L;
        while (true) {
            PageResult page = dingTalkApi.listUsersByDepartment(deptId, start, DEFAULT_PAGE_SIZE);
            users.addAll(page.getList());
            if (!page.getHasMore()) {
                break;
            }
            start = page.getNextCursor();
        }
        return users;
    }

    private record DepartmentPair(long deptId, Department department) {}
}
