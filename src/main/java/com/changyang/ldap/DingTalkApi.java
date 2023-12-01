package com.changyang.ldap;

import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.request.OapiV2DepartmentGetRequest;
import com.dingtalk.api.request.OapiV2DepartmentListsubRequest;
import com.dingtalk.api.request.OapiV2UserListRequest;
import com.dingtalk.api.response.OapiGettokenResponse;
import com.dingtalk.api.response.OapiV2DepartmentGetResponse;
import com.dingtalk.api.response.OapiV2DepartmentGetResponse.DeptGetResponse;
import com.dingtalk.api.response.OapiV2DepartmentListsubResponse;
import com.dingtalk.api.response.OapiV2DepartmentListsubResponse.DeptBaseResponse;
import com.dingtalk.api.response.OapiV2UserListResponse;
import com.dingtalk.api.response.OapiV2UserListResponse.PageResult;
import com.taobao.api.ApiException;
import com.taobao.api.TaobaoRequest;
import com.taobao.api.TaobaoResponse;
import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * 钉钉接口
 *
 * @author wangsg
 **/
@Slf4j
public class DingTalkApi {

    public static final long ROOT_DEPARTMENT_ID = 1L;

    private static final String HOST = "https://oapi.dingtalk.com";
    private static final String LANGUAGE = "zh_CN";

    private final AppCertificate certificate;
    private volatile AccessToken token;

    public DingTalkApi(AppCertificate certificate) {
        this.certificate = certificate;
        this.token = getToken(certificate);
    }

    /**
     * 根据部门ID获取指定部门详情
     *
     * @param deptId 部门ID
     * @see <a href="https://open.dingtalk.com/document/orgapp/query-department-details0-v2">获取部门详情</a>
     */
    public DeptGetResponse getDepartmentDetail(long deptId) {
        OapiV2DepartmentGetRequest req = new OapiV2DepartmentGetRequest();
        req.setDeptId(deptId);
        req.setLanguage(LANGUAGE);
        OapiV2DepartmentGetResponse resp = execute("/topapi/v2/department/get", req, currentToken());
        if (!resp.isSuccess()) {
            log.error("cannot get department detail, code={}, msg={}", resp.getErrorCode(), resp.getMsg());
            throw new DingTalkApiException(resp.getErrcode(), resp.getMsg());
        }
        return resp.getResult();
    }

    /**
     * 获取下一级部门基础信息列表
     *
     * @param deptId 父部门ID
     * @see <a href="https://open.dingtalk.com/document/orgapp/obtain-the-department-list-v2">获取部门列表</a>
     */
    public List<DeptBaseResponse> listSubDepartments(long deptId) {
        OapiV2DepartmentListsubRequest req = new OapiV2DepartmentListsubRequest();
        req.setDeptId(deptId);
        req.setLanguage(LANGUAGE);
        OapiV2DepartmentListsubResponse resp = execute("/topapi/v2/department/listsub", req, currentToken());
        if (!resp.isSuccess()) {
            log.error("cannot list sub departments, code={}, msg={}", resp.getErrorCode(), resp.getMsg());
            throw new DingTalkApiException(resp.getErrcode(), resp.getMsg());
        }
        return resp.getResult();
    }

    /**
     * 获取指定部门中的用户详细信息
     *
     * @param deptId 部门ID
     * @param cursor 分页查询游标，首次应为0，后续为 {@link PageResult#getNextCursor()}
     * @param size   分页大小，最大100
     * @see <a
     * href="https://open.dingtalk.com/document/orgapp/queries-the-complete-information-of-a-department-user">获取部门用户详情</a>
     */
    public PageResult listUsersByDepartment(long deptId, long cursor, long size) {
        OapiV2UserListRequest req = new OapiV2UserListRequest();
        req.setDeptId(deptId);
        req.setCursor(cursor);
        req.setSize(size);
        req.setOrderField("modify_desc");
        req.setLanguage(LANGUAGE);
        OapiV2UserListResponse resp = execute("/topapi/v2/user/list", req, currentToken());
        if (!resp.isSuccess()) {
            log.error("cannot list users of department, code={}, msg={}", resp.getErrorCode(), resp.getMsg());
            throw new DingTalkApiException(resp.getErrcode(), resp.getMsg());
        }
        return resp.getResult();
    }

    private String currentToken() {
        if (token == null || token.expireAt().isBefore(Instant.now())) {
            this.token = getToken(certificate);
        }
        return token.token();
    }

    private AccessToken getToken(AppCertificate certificate) {
        OapiGettokenRequest req = new OapiGettokenRequest();
        req.setAppkey(certificate.key());
        req.setAppsecret(certificate.secret());
        req.setHttpMethod("GET");
        Instant start = Instant.now();
        OapiGettokenResponse resp = execute("/gettoken", req, null);
        if (!resp.isSuccess()) {
            log.error("cannot get token, code={}, msg={}", resp.getErrorCode(), resp.getMsg());
            throw new DingTalkApiException(resp.getErrcode(), resp.getMsg());
        }
        Instant expireAt = start.plus(resp.getExpiresIn(), ChronoUnit.SECONDS);
        return new AccessToken(resp.getAccessToken(), expireAt);
    }

    private <T extends TaobaoResponse> T execute(String uri, TaobaoRequest<T> request, String accessToken) {
        log.debug("retrieve data from ding-talk, uri={}", uri);
        DingTalkClient client = new DefaultDingTalkClient(HOST + uri);
        try {
            return client.execute(request, accessToken);
        } catch (ApiException e) {
            log.error("cannot retrieve data from ding-talk, uri={}, error={}", uri, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public record AccessToken(@NonNull String token, @NonNull Instant expireAt) implements Serializable {}

    /**
     * 钉钉接口访问异常
     **/
    @Getter
    public static class DingTalkApiException extends RuntimeException {

        private final long code;

        public DingTalkApiException(long code, String message) {
            super(message);
            this.code = code;
        }
    }
}
