package com.alibaba.higress.sdk.service.kubernetes;

import static com.alibaba.higress.sdk.service.kubernetes.KubernetesUtil.buildDomainLabelSelector;
import static com.alibaba.higress.sdk.service.kubernetes.KubernetesUtil.buildLabelSelector;
import static com.alibaba.higress.sdk.service.kubernetes.KubernetesUtil.joinLabelSelectors;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.higress.sdk.config.HigressServiceConfig;
import com.alibaba.higress.sdk.constant.HigressConstants;
import com.alibaba.higress.sdk.constant.KubernetesConstants;
import com.alibaba.higress.sdk.constant.KubernetesConstants.Annotation;
import com.alibaba.higress.sdk.constant.KubernetesConstants.Label;
import com.alibaba.higress.sdk.constant.Separators;
import com.alibaba.higress.sdk.exception.BusinessException;
import com.alibaba.higress.sdk.http.HttpStatus;
import com.alibaba.higress.sdk.service.kubernetes.crd.istio.V1alpha3EnvoyFilter;
import com.alibaba.higress.sdk.service.kubernetes.crd.mcp.V1McpBridge;
import com.alibaba.higress.sdk.service.kubernetes.crd.mcp.V1McpBridgeList;
import com.alibaba.higress.sdk.service.kubernetes.crd.wasm.V1alpha1WasmPlugin;
import com.alibaba.higress.sdk.service.kubernetes.crd.wasm.V1alpha1WasmPluginList;
import com.alibaba.higress.sdk.service.kubernetes.model.IstioEndpointShard;
import com.alibaba.higress.sdk.service.kubernetes.model.RegistryzService;
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.V1APIResource;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1IngressList;
import io.kubernetes.client.openapi.models.V1IngressSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.Strings;
import io.kubernetes.client.util.Yaml;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Kubernetes客户端服务类，提供对Kubernetes资源的CRUD操作
 */
@Slf4j
public class KubernetesClientService {

    // 能力检查尝试次数
    private static final int CAPABILITY_CHECK_ATTEMPTS = 5;
    // 能力检查间隔时间(毫秒)
    private static final long CAPABILITY_CHECK_INTERVAL = 1000;
    // 默认kubeconfig文件路径
    private static final String KUBE_CONFIG_DEFAULT_PATH =
        Paths.get(System.getProperty("user.home"), "/.kube/config").toString();
    // Pod服务账户令牌文件路径
    private static final String POD_SERVICE_ACCOUNT_TOKEN_FILE_PATH =
        "/var/run/secrets/kubernetes.io/serviceaccount/token";
    // 控制器访问令牌文件路径
    private static final String CONTROLLER_ACCESS_TOKEN_FILE_PATH = "/var/run/secrets/access-token/token";
    // 默认标签选择器
    private static final String DEFAULT_LABEL_SELECTORS =
        buildLabelSelector(KubernetesConstants.Label.RESOURCE_DEFINER_KEY, Label.RESOURCE_DEFINER_VALUE);

    // Kubernetes API客户端
    private ApiClient client;

    // HTTP客户端
    private final OkHttpClient okHttpClient = new OkHttpClient();

    // 是否为集群内模式
    private final Boolean inClusterMode;

    // kubeconfig文件路径
    private final String kubeConfig;

    // kubeconfig内容
    private final String kubeConfigContent;

    // 控制器服务名称
    private final String controllerServiceName;

    // 控制器命名空间
    private final String controllerNamespace;

    // 控制器监听的Ingress类名
    private final String controllerWatchedIngressClassName;

    // 控制器监听的命名空间
    private final String controllerWatchedNamespace;

    // 控制器服务主机
    private final String controllerServiceHost;

    // 控制器服务端口
    private final int controllerServicePort;

    // 控制器JWT策略
    private final String controllerJwtPolicy;

    // 控制器访问令牌
    private final String controllerAccessToken;

    // Ingress监听谓词
    private final Predicate<V1Ingress> isIngressWatched;

    // 默认Ingress类
    private final String defaultIngressClass;

    // Ingress v1是否支持
    @Getter
    private boolean ingressV1Supported;

    // 集群域名后缀
    @Getter
    private final String clusterDomainSuffix;

    /**
     * 构造函数，初始化Kubernetes客户端服务
     * @param config Higress服务配置
     * @throws IOException IO异常
     */
    public KubernetesClientService(HigressServiceConfig config) throws IOException {
        validateConfig(config);

        this.kubeConfig = config.getKubeConfigPath();
        this.kubeConfigContent = config.getKubeConfigContent();
        this.controllerNamespace = config.getControllerNamespace();
        this.controllerServiceName = config.getControllerServiceName();
        this.controllerServiceHost = config.getControllerServiceHost();
        this.controllerServicePort = config.getControllerServicePort();
        this.controllerWatchedIngressClassName = config.getControllerWatchedIngressClassName();
        this.controllerWatchedNamespace = config.getControllerWatchedNamespace();
        this.controllerJwtPolicy = config.getControllerJwtPolicy();
        this.controllerAccessToken = config.getControllerAccessToken();
        this.inClusterMode =
            Strings.isNullOrEmpty(kubeConfig) && Strings.isNullOrEmpty(kubeConfigContent) && isInCluster();
        this.isIngressWatched = buildIsIngressWatchedPredicate(this.controllerWatchedIngressClassName);
        this.defaultIngressClass = StringUtils.firstNonEmpty(this.controllerWatchedIngressClassName,
            HigressConstants.CONTROLLER_INGRESS_CLASS_NAME_DEFAULT);
        this.clusterDomainSuffix = config.getClusterDomainSuffix();

        // 根据模式初始化客户端
        if (inClusterMode) {
            client = ClientBuilder.cluster().build();
            log.info("init KubernetesClientService with InCluster mode");
        } else {
            if (Strings.isNullOrEmpty(kubeConfigContent)) {
                String kubeConfigPath = !Strings.isNullOrEmpty(kubeConfig) ? kubeConfig : KUBE_CONFIG_DEFAULT_PATH;
                try (FileReader reader = new FileReader(kubeConfigPath)) {
                    client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(reader)).build();
                }
                log.info("init KubernetesClientService with KubeConfig: {}", kubeConfigPath);
            } else {
                try (StringReader reader = new StringReader(kubeConfigContent)) {
                    client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(reader)).build();
                }
                log.info("init KubernetesClientService with KubeConfigContent: {}", kubeConfigContent);
            }
        }

        initializeK8sCapabilities();
    }

    /**
     * 初始化Kubernetes能力
     */
    private void initializeK8sCapabilities() {
        Exception lastException = null;
        for (int i = 0; i < CAPABILITY_CHECK_ATTEMPTS; i++) {
            try {
                NetworkingV1Api networkingV1Api = new NetworkingV1Api(client);
                List<V1APIResource> networkingV1ApiResources = networkingV1Api.getAPIResources().getResources();
                ingressV1Supported = CollectionUtils.isNotEmpty(networkingV1ApiResources)
                    && networkingV1ApiResources.stream().anyMatch(r -> "Ingress".equals(r.getKind()));
                return;
            } catch (Exception e) {
                lastException = e;
                try {
                    Thread.sleep(CAPABILITY_CHECK_INTERVAL);
                } catch (InterruptedException ex) {
                    // 忽略中断异常
                }
            }
        }
        log.error("Failed to load NetworkingV1 API resources from K8s.", lastException);
        // Ingress v1 API自Kubernetes v1.19(2020年8月26日发布)起支持
        // 如果无法确定是否支持，我们假设它是支持的
        ingressV1Supported = true;
    }

    /**
     * 检查命名空间是否受保护
     * @param namespace 命名空间名称
     * @return 是否受保护
     */
    public boolean isNamespaceProtected(String namespace) {
        return KubernetesConstants.KUBE_SYSTEM_NS.equals(namespace) || controllerNamespace.equals(namespace);
    }

    /**
     * 检查Kubernetes对象是否由控制台定义
     * @param metadata Kubernetes对象
     * @return 是否由控制台定义
     */
    public boolean isDefinedByConsole(KubernetesObject metadata) {
        return isDefinedByConsole(metadata.getMetadata());
    }

    /**
     * 检查对象元数据是否由控制台定义
     * @param metadata 对象元数据
     * @return 是否由控制台定义
     */
    public boolean isDefinedByConsole(V1ObjectMeta metadata) {
        return metadata != null && controllerNamespace.equals(metadata.getNamespace())
            && Label.RESOURCE_DEFINER_VALUE.equals(KubernetesUtil.getLabel(metadata, Label.RESOURCE_DEFINER_KEY));
    }

    /**
     * 从YAML字符串加载Kubernetes对象
     * @param yaml YAML字符串
     * @param clazz 对象类型
     * @param <T> Kubernetes对象类型
     * @return Kubernetes对象
     */
    public <T extends KubernetesObject> T loadFromYaml(String yaml, Class<T> clazz) {
        return Yaml.getSnakeYaml(clazz).loadAs(yaml, clazz);
    }

    /**
     * 从JSON字符串加载Kubernetes对象
     * @param json JSON字符串
     * @param clazz 对象类型
     * @param <T> Kubernetes对象类型
     * @return Kubernetes对象
     */
    public <T extends KubernetesObject> T loadFromJson(String json, Class<T> clazz) {
        return client.getJSON().deserialize(json, clazz);
    }

    /**
     * 将Kubernetes对象保存为YAML字符串
     * @param obj Kubernetes对象
     * @return YAML字符串
     */
    public String saveToYaml(KubernetesObject obj) {
        return Yaml.getSnakeYaml(obj.getClass()).dumpAsMap(obj);
    }

    /**
     * 将Kubernetes对象保存为JSON字符串
     * @param obj Kubernetes对象
     * @return JSON字符串
     */
    public String saveToJson(KubernetesObject obj) {
        return client.getJSON().serialize(obj);
    }

    /**
     * 获取网关服务列表
     * @return 注册服务列表
     * @throws IOException IO异常
     */
    public List<RegistryzService> gatewayServiceList() throws IOException {
        Request request = buildControllerRequest("/debug/registryz");
        log.info("gatewayServiceList url {}", request.url());
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new BusinessException(
                    "Failed to get gateway service list from controller. Code=" + response.code());
            }
            if (response.body() == null) {
                throw new BusinessException("Empty response got from controller when loading gateway service list.");
            }
            String responseString = new String(response.body().bytes());
            if (StringUtils.isNotEmpty(responseString)) {
                return JSON.parseArray(responseString, RegistryzService.class);
            }
        }
        return null;
    }

    /**
     * 获取网关服务端点
     * @return 服务端点映射
     * @throws IOException IO异常
     */
    public Map<String, Map<String, IstioEndpointShard>> gatewayServiceEndpoint() throws IOException {
        Request request = buildControllerRequest("/debug/endpointShardz");
        log.info("gatewayServiceEndpoint url {}", request.url());
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new BusinessException("Failed to get service endpoints from controller. Code=" + response.code());
            }
            if (response.body() == null) {
                throw new BusinessException("Empty response got from controller when loading service endpoints.");
            }
            String responseString = new String(response.body().bytes());
            if (StringUtils.isNotEmpty(responseString)) {
                return JSON.parseObject(responseString,
                    new TypeReference<Map<String, Map<String, IstioEndpointShard>>>() {});
            }
        }
        return null;
    }

    /**
     * 检查是否在集群内运行
     * @return 是否在集群内
     */
    private static boolean isInCluster() {
        return new File(POD_SERVICE_ACCOUNT_TOKEN_FILE_PATH).exists();
    }

    /**
     * 列出所有Ingress资源
     * @return Ingress列表
     * @throws ApiException API异常
     */
    public List<V1Ingress> listAllIngresses() throws ApiException {
        List<V1Ingress> ingresses = new ArrayList<>();
        NetworkingV1Api apiInstance = new NetworkingV1Api(client);
        if (StringUtils.isEmpty(controllerWatchedNamespace)) {
            V1IngressList list =
                apiInstance.listIngressForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
            ingresses.addAll(list.getItems());
        } else {
            for (String ns : Lists.newArrayList(controllerNamespace, controllerWatchedNamespace)) {
                V1IngressList list =
                    apiInstance.listNamespacedIngress(ns, null, null, null, null, null, null, null, null, null, null);
                if (list != null) {
                    ingresses.addAll(list.getItems());
                }
            }
        }
        retainWatchedIngress(ingresses);
        return sortKubernetesObjects(ingresses);
    }

    /**
     * 列出所有服务
     * @return 服务列表
     * @throws ApiException API异常
     */
    public List<V1Service> listAllServiceList() throws ApiException {
        CoreV1Api coreV1Api = new CoreV1Api(client);
        V1ServiceList v1ServiceList =
            coreV1Api.listServiceForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
        if (Objects.isNull(v1ServiceList)) {
            return Collections.emptyList();
        }
        List<V1Service> resultList = new ArrayList<>(v1ServiceList.getItems());
        resultList.removeIf(v1Service -> StringUtils.startsWith(v1Service.getMetadata().getNamespace(), "kube"));
        if (StringUtils.isNotEmpty(controllerNamespace)) {
            resultList.removeIf(v1Service -> controllerNamespace.equals(v1Service.getMetadata().getNamespace()));
        }
        return sortKubernetesObjects(resultList);
    }

    /**
     * 列出所有端点
     * @return 端点列表
     * @throws ApiException API异常
     */
    @SneakyThrows
    public List<V1Endpoints> listAllEndPointsList() {
        CoreV1Api coreV1Api = new CoreV1Api(client);
        V1EndpointsList v1EndpointsList =
            coreV1Api.listEndpointsForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
        if (Objects.isNull(v1EndpointsList)) {
            return Collections.emptyList();
        }
        List<V1Endpoints> resultList = new ArrayList<>(v1EndpointsList.getItems());
        resultList.removeIf(v1Service -> StringUtils.startsWith(v1Service.getMetadata().getNamespace(), "kube"));
        if (StringUtils.isNotEmpty(controllerNamespace)) {
            resultList.removeIf(v1Service -> controllerNamespace.equals(v1Service.getMetadata().getNamespace()));
        }
        return sortKubernetesObjects(resultList);
    }

    /**
     * 列出Ingress资源
     * @return Ingress列表
     * @throws ApiException API异常
     */
    public List<V1Ingress> listIngress() throws ApiException {
        NetworkingV1Api apiInstance = new NetworkingV1Api(client);
        V1IngressList list = apiInstance.listNamespacedIngress(controllerNamespace, null, null, null, null,
            DEFAULT_LABEL_SELECTORS, null, null, null, null, null);
        if (list == null) {
            return Collections.emptyList();
        }
        List<V1Ingress> ingresses = new ArrayList<>(list.getItems());
        retainWatchedIngress(ingresses);
        return sortKubernetesObjects(ingresses);
    }

    /**
     * 根据标签映射列出Ingress资源
     * @param labelMap 标签映射
     * @return Ingress列表
     * @throws ApiException API异常
     */
    public List<V1Ingress> listIngress(Map<String, String> labelMap) throws ApiException {
        NetworkingV1Api apiInstance = new NetworkingV1Api(client);
        String labelSelectors = null;
        if (MapUtils.isNotEmpty(labelMap)) {
            List<String> labelSelectorsList = labelMap.keySet().stream()
                .map(key -> buildLabelSelector(key, labelMap.get(key))).collect(Collectors.toList());
            labelSelectorsList.add(DEFAULT_LABEL_SELECTORS);
            labelSelectors = String.join(Separators.COMMA, labelSelectorsList);
        } else {
            labelSelectors = DEFAULT_LABEL_SELECTORS;
        }
        V1IngressList list = apiInstance.listNamespacedIngress(controllerNamespace, null, null, null, null,
            labelSelectors, null, null, null, null, null);
        if (list == null) {
            return Collections.emptyList();
        }
        List<V1Ingress> ingresses = new ArrayList<>(list.getItems());
        retainWatchedIngress(ingresses);
        return sortKubernetesObjects(ingresses);
    }

    /**
     * 根据域名列出Ingress资源
     * @param domainName 域名
     * @return Ingress列表
     * @throws ApiException API异常
     */
    public List<V1Ingress> listIngressByDomain(String domainName) throws ApiException {
        NetworkingV1Api apiInstance = new NetworkingV1Api(client);
        String labelSelectors = joinLabelSelectors(DEFAULT_LABEL_SELECTORS, buildDomainLabelSelector(domainName));
        V1IngressList list = apiInstance.listNamespacedIngress(controllerNamespace, null, null, null, null,
            labelSelectors, null, null, null, null, null);
        if (list == null) {
            return Collections.emptyList();
        }
        List<V1Ingress> ingresses = new ArrayList<>(list.getItems());
        retainWatchedIngress(ingresses);
        return sortKubernetesObjects(ingresses);
    }

    /**
     * 读取Ingress资源
     * @param name Ingress名称
     * @return Ingress对象
     * @throws ApiException API异常
     */
    public V1Ingress readIngress(String name) throws ApiException {
        NetworkingV1Api apiInstance = new NetworkingV1Api(client);
        try {
            return apiInstance.readNamespacedIngress(name, controllerNamespace, null);
        } catch (ApiException e) {
            if (e.getCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 创建Ingress资源
     * @param ingress Ingress对象
     * @return 创建的Ingress对象
     * @throws ApiException API异常
     */
    public V1Ingress createIngress(V1Ingress ingress) throws ApiException {
        renderDefaultMetadata(ingress);
        fillDefaultIngressClass(ingress);
        NetworkingV1Api apiInstance = new NetworkingV1Api(client);
        return apiInstance.createNamespacedIngress(controllerNamespace, ingress, null, null, null, null);
    }

    /**
     * 替换Ingress资源
     * @param ingress Ingress对象
     * @return 替换后的Ingress对象
     * @throws ApiException API异常
     */
    public V1Ingress replaceIngress(V1Ingress ingress) throws ApiException {
        V1ObjectMeta metadata = ingress.getMetadata();
        if (metadata == null) {
            throw new IllegalArgumentException("Ingress doesn't have a valid metadata.");
        }
        renderDefaultMetadata(ingress);
        fillDefaultIngressClass(ingress);
        NetworkingV1Api apiInstance = new NetworkingV1Api(client);
        return apiInstance.replaceNamespacedIngress(metadata.getName(), controllerNamespace, ingress, null, null, null,
            null);
    }

    /**
     * 删除Ingress资源
     * @param name Ingress名称
     * @throws ApiException API异常
     */
    public void deleteIngress(String name) throws ApiException {
        NetworkingV1Api apiInstance = new NetworkingV1Api(client);
        V1Status status;
        try {
            status = apiInstance.deleteNamespacedIngress(name, controllerNamespace, null, null, null, null, null, null);
        } catch (ApiException ae) {
            if (ae.getCode() == HttpStatus.NOT_FOUND) {
                // 要删除的Ingress已经不存在
                return;
            }
            throw ae;
        }
        checkResponseStatus(status);
    }

    /**
     * 列出ConfigMap资源
     * @return ConfigMap列表
     * @throws ApiException API异常
     */
    public List<V1ConfigMap> listConfigMap() throws ApiException {
        return listConfigMap(null);
    }

    /**
     * 根据标签选择器列出ConfigMap资源
     * @param labelSelectors 标签选择器
     * @return ConfigMap列表
     * @throws ApiException API异常
     */
    public List<V1ConfigMap> listConfigMap(Map<String, String> labelSelectors) throws ApiException {
        CoreV1Api coreV1Api = new CoreV1Api(client);
        String labelSelectorsStr = KubernetesUtil.joinLabelSelectors(DEFAULT_LABEL_SELECTORS,
            KubernetesUtil.buildLabelSelectors(labelSelectors));
        V1ConfigMapList list = coreV1Api.listNamespacedConfigMap(controllerNamespace, null, null, null, null,
            labelSelectorsStr, null, null, null, null, null);
        return sortKubernetesObjects(Optional.ofNullable(list.getItems()).orElse(Collections.emptyList()));
    }

    /**
     * 创建ConfigMap资源
     * @param configMap ConfigMap对象
     * @return 创建的ConfigMap对象
     * @throws ApiException API异常
     */
    public V1ConfigMap createConfigMap(V1ConfigMap configMap) throws ApiException {
        renderDefaultMetadata(configMap);
        CoreV1Api coreV1Api = new CoreV1Api(client);
        return coreV1Api.createNamespacedConfigMap(controllerNamespace, configMap, null, null, null, null);
    }

    /**
     * 读取ConfigMap资源
     * @param name ConfigMap名称
     * @return ConfigMap对象
     * @throws ApiException API异常
     */
    public V1ConfigMap readConfigMap(String name) throws ApiException {
        CoreV1Api coreV1Api = new CoreV1Api(client);
        try {
            return coreV1Api.readNamespacedConfigMap(name, controllerNamespace, null);
        } catch (ApiException e) {
            if (e.getCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 删除ConfigMap资源
     * @param name ConfigMap名称
     * @throws ApiException API异常
     */
    public void deleteConfigMap(String name) throws ApiException {
        CoreV1Api coreV1Api = new CoreV1Api(client);
        V1Status status;
        try {
            status = coreV1Api.deleteNamespacedConfigMap(name, controllerNamespace, null, null, null, null, null, null);
        } catch (ApiException ae) {
            if (ae.getCode() == HttpStatus.NOT_FOUND) {
                // 要删除的ConfigMap已经不存在
                return;
            }
            throw ae;
        }
        checkResponseStatus(status);
    }

    /**
     * 替换ConfigMap资源
     * @param configMap ConfigMap对象
     * @return 替换后的ConfigMap对象
     * @throws ApiException API异常
     */
    public V1ConfigMap replaceConfigMap(V1ConfigMap configMap) throws ApiException {
        V1ObjectMeta metadata = configMap.getMetadata();
        if (metadata == null) {
            throw new IllegalArgumentException("ConfigMap doesn't have a valid metadata.");
        }
        renderDefaultMetadata(configMap);
        CoreV1Api coreV1Api = new CoreV1Api(client);
        return coreV1Api.replaceNamespacedConfigMap(metadata.getName(), controllerNamespace, configMap, null, null,
            null, null);
    }

    /**
     * 列出Secret资源
     * @param type Secret类型
     * @return Secret列表
     * @throws ApiException API异常
     */
    public List<V1Secret> listSecret(String type) throws ApiException {
        CoreV1Api coreV1Api = new CoreV1Api(client);
        String fieldSelectors = null;
        if (StringUtils.isNotEmpty(type)) {
            fieldSelectors = KubernetesConstants.TYPE_FIELD + Separators.EQUALS_SIGN + type;
        }
        V1SecretList list = coreV1Api.listNamespacedSecret(controllerNamespace, null, null, null, fieldSelectors, null,
            null, null, null, null, null);
        return sortKubernetesObjects(Optional.ofNullable(list.getItems()).orElse(Collections.emptyList()));
    }

    /**
     * 读取Secret资源
     * @param name Secret名称
     * @return Secret对象
     * @throws ApiException API异常
     */
    public V1Secret readSecret(String name) throws ApiException {
        CoreV1Api coreV1Api = new CoreV1Api(client);
        try {
            return coreV1Api.readNamespacedSecret(name, controllerNamespace, null);
        } catch (ApiException e) {
            if (e.getCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 创建Secret资源
     * @param secret Secret对象
     * @return 创建的Secret对象
     * @throws ApiException API异常
     */
    public V1Secret createSecret(V1Secret secret) throws ApiException {
        renderDefaultMetadata(secret);
        CoreV1Api coreV1Api = new CoreV1Api(client);
        return coreV1Api.createNamespacedSecret(controllerNamespace, secret, null, null, null, null);
    }

    /**
     * 替换Secret资源
     * @param secret Secret对象
     * @return 替换后的Secret对象
     * @throws ApiException API异常
     */
    public V1Secret replaceSecret(V1Secret secret) throws ApiException {
        V1ObjectMeta metadata = secret.getMetadata();
        if (metadata == null) {
            throw new IllegalArgumentException("Secret doesn't have a valid metadata.");
        }
        renderDefaultMetadata(secret);
        CoreV1Api coreV1Api = new CoreV1Api(client);
        return coreV1Api.replaceNamespacedSecret(metadata.getName(), controllerNamespace, secret, null, null, null,
            null);
    }

    /**
     * 删除Secret资源
     * @param name Secret名称
     * @throws ApiException API异常
     */
    public void deleteSecret(String name) throws ApiException {
        CoreV1Api coreV1Api = new CoreV1Api(client);
        V1Status status;
        try {
            status = coreV1Api.deleteNamespacedSecret(name, controllerNamespace, null, null, null, null, null, null);
        } catch (ApiException ae) {
            if (ae.getCode() == HttpStatus.NOT_FOUND) {
                // 要删除的Secret已经不存在
                return;
            }
            throw ae;
        }
        checkResponseStatus(status);
    }

    /**
     * 列出McpBridge资源
     * @return McpBridge列表
     */
    public List<V1McpBridge> listMcpBridge() {
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        try {
            Object response = customObjectsApi.listNamespacedCustomObject(V1McpBridge.API_GROUP, V1McpBridge.VERSION,
                controllerNamespace, V1McpBridge.PLURAL, null, null, null, null, null, null, null, null, null, null);
            io.kubernetes.client.openapi.JSON json = new io.kubernetes.client.openapi.JSON();
            V1McpBridgeList list = json.deserialize(json.serialize(response), V1McpBridgeList.class);
            return sortKubernetesObjects(list.getItems());
        } catch (ApiException e) {
            log.error("listMcpBridge Status code: " + e.getCode() + "Reason: " + e.getResponseBody()
                + "Response headers: " + e.getResponseHeaders(), e);
            return null;
        }
    }

    /**
     * 创建McpBridge资源
     * @param mcpBridge McpBridge对象
     * @return 创建的McpBridge对象
     * @throws ApiException API异常
     */
    public V1McpBridge createMcpBridge(V1McpBridge mcpBridge) throws ApiException {
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        Object response = customObjectsApi.createNamespacedCustomObject(V1McpBridge.API_GROUP, V1McpBridge.VERSION,
            controllerNamespace, V1McpBridge.PLURAL, mcpBridge, null, null, null);
        return client.getJSON().deserialize(client.getJSON().serialize(response), V1McpBridge.class);
    }

    /**
     * 替换McpBridge资源
     * @param mcpBridge McpBridge对象
     * @return 替换后的McpBridge对象
     * @throws ApiException API异常
     */
    public V1McpBridge replaceMcpBridge(V1McpBridge mcpBridge) throws ApiException {
        V1ObjectMeta metadata = mcpBridge.getMetadata();
        if (metadata == null) {
            throw new IllegalArgumentException("mcpBridge doesn't have a valid metadata.");
        }
        metadata.setNamespace(controllerNamespace);
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        Object response = customObjectsApi.replaceNamespacedCustomObject(V1McpBridge.API_GROUP, V1McpBridge.VERSION,
            controllerNamespace, V1McpBridge.PLURAL, metadata.getName(), mcpBridge, null, null);
        return client.getJSON().deserialize(client.getJSON().serialize(response), V1McpBridge.class);
    }

    /**
     * 删除McpBridge资源
     * @param name McpBridge名称
     * @throws ApiException API异常
     */
    public void deleteMcpBridge(String name) throws ApiException {
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        customObjectsApi.deleteNamespacedCustomObject(V1McpBridge.API_GROUP, V1McpBridge.VERSION, controllerNamespace,
            V1McpBridge.PLURAL, name, null, null, null, null, null);
    }

    /**
     * 读取McpBridge资源
     * @param name McpBridge名称
     * @return McpBridge对象
     * @throws ApiException API异常
     */
    public V1McpBridge readMcpBridge(String name) throws ApiException {
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        try {
            Object response = customObjectsApi.getNamespacedCustomObject(V1McpBridge.API_GROUP, V1McpBridge.VERSION,
                controllerNamespace, V1McpBridge.PLURAL, name);
            return client.getJSON().deserialize(client.getJSON().serialize(response), V1McpBridge.class);
        } catch (ApiException e) {
            if (e.getCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 列出WasmPlugin资源
     * @return WasmPlugin列表
     * @throws ApiException API异常
     */
    public List<V1alpha1WasmPlugin> listWasmPlugin() throws ApiException {
        return listWasmPlugin(null, null, null);
    }

    /**
     * 根据名称列出WasmPlugin资源
     * @param name 插件名称
     * @return WasmPlugin列表
     * @throws ApiException API异常
     */
    public List<V1alpha1WasmPlugin> listWasmPlugin(String name) throws ApiException {
        return listWasmPlugin(name, null, null);
    }

    /**
     * 根据名称和版本列出WasmPlugin资源
     * @param name 插件名称
     * @param version 插件版本
     * @return WasmPlugin列表
     * @throws ApiException API异常
     */
    public List<V1alpha1WasmPlugin> listWasmPlugin(String name, String version) throws ApiException {
        return listWasmPlugin(name, version, null);
    }

    /**
     * 根据名称、版本和内置标志列出WasmPlugin资源
     * @param name 插件名称
     * @param version 插件版本
     * @param builtIn 是否为内置插件
     * @return WasmPlugin列表
     * @throws ApiException API异常
     */
    public List<V1alpha1WasmPlugin> listWasmPlugin(String name, String version, Boolean builtIn) throws ApiException {
        List<String> labelSelectorItems = new ArrayList<>();
        labelSelectorItems.add(DEFAULT_LABEL_SELECTORS);
        if (StringUtils.isNotEmpty(name)) {
            labelSelectorItems.add(buildLabelSelector(Label.WASM_PLUGIN_NAME_KEY, name));
        }
        if (StringUtils.isNotEmpty(version)) {
            labelSelectorItems.add(buildLabelSelector(Label.WASM_PLUGIN_VERSION_KEY, version));
        }
        if (builtIn != null) {
            labelSelectorItems.add(buildLabelSelector(Label.WASM_PLUGIN_BUILT_IN_KEY, String.valueOf(builtIn)));
        }
        String labelSelector = labelSelectorItems.size() == 1 ? labelSelectorItems.get(0)
            : joinLabelSelectors(labelSelectorItems.toArray(new String[0]));
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        Object response = customObjectsApi.listNamespacedCustomObject(V1alpha1WasmPlugin.API_GROUP,
            V1alpha1WasmPlugin.VERSION, controllerNamespace, V1alpha1WasmPlugin.PLURAL, null, null, null, null,
            labelSelector, null, null, null, null, null);
        io.kubernetes.client.openapi.JSON json = new io.kubernetes.client.openapi.JSON();
        V1alpha1WasmPluginList list = json.deserialize(json.serialize(response), V1alpha1WasmPluginList.class);
        return sortKubernetesObjects(list.getItems());
    }

    /**
     * 创建WasmPlugin资源
     * @param plugin WasmPlugin对象
     * @return 创建的WasmPlugin对象
     * @throws ApiException API异常
     */
    public V1alpha1WasmPlugin createWasmPlugin(V1alpha1WasmPlugin plugin) throws ApiException {
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        renderDefaultMetadata(plugin);
        Object response = customObjectsApi.createNamespacedCustomObject(V1alpha1WasmPlugin.API_GROUP,
            V1alpha1WasmPlugin.VERSION, controllerNamespace, V1alpha1WasmPlugin.PLURAL, plugin, null, null, null);
        return client.getJSON().deserialize(client.getJSON().serialize(response), V1alpha1WasmPlugin.class);
    }

    /**
     * 替换WasmPlugin资源
     * @param plugin WasmPlugin对象
     * @return 替换后的WasmPlugin对象
     * @throws ApiException API异常
     */
    public V1alpha1WasmPlugin replaceWasmPlugin(V1alpha1WasmPlugin plugin) throws ApiException {
        V1ObjectMeta metadata = plugin.getMetadata();
        if (metadata == null) {
            throw new IllegalArgumentException("WasmPlugin doesn't have a valid metadata.");
        }
        renderDefaultMetadata(plugin);
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        Object response =
            customObjectsApi.replaceNamespacedCustomObject(V1alpha1WasmPlugin.API_GROUP, V1alpha1WasmPlugin.VERSION,
                controllerNamespace, V1alpha1WasmPlugin.PLURAL, metadata.getName(), plugin, null, null);
        return client.getJSON().deserialize(client.getJSON().serialize(response), V1alpha1WasmPlugin.class);
    }

    /**
     * 删除WasmPlugin资源
     * @param name WasmPlugin名称
     * @throws ApiException API异常
     */
    public void deleteWasmPlugin(String name) throws ApiException {
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        try {
            customObjectsApi.deleteNamespacedCustomObject(V1alpha1WasmPlugin.API_GROUP, V1alpha1WasmPlugin.VERSION,
                controllerNamespace, V1alpha1WasmPlugin.PLURAL, name, null, null, null, null, null);
        } catch (ApiException e) {
            if (e.getCode() != HttpStatus.NOT_FOUND) {
                throw e;
            }
        }
    }

    /**
     * 读取WasmPlugin资源
     * @param name WasmPlugin名称
     * @return WasmPlugin对象
     * @throws ApiException API异常
     */
    public V1alpha1WasmPlugin readWasmPlugin(String name) throws ApiException {
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        try {
            Object response = customObjectsApi.getNamespacedCustomObject(V1alpha1WasmPlugin.API_GROUP,
                V1alpha1WasmPlugin.VERSION, controllerNamespace, V1alpha1WasmPlugin.PLURAL, name);
            return client.getJSON().deserialize(client.getJSON().serialize(response), V1alpha1WasmPlugin.class);
        } catch (ApiException e) {
            if (e.getCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 创建EnvoyFilter资源
     * @param filter EnvoyFilter对象
     * @return 创建的EnvoyFilter对象
     * @throws ApiException API异常
     */
    public V1alpha3EnvoyFilter createEnvoyFilter(V1alpha3EnvoyFilter filter) throws ApiException {
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        renderDefaultMetadata(filter);
        Object response = customObjectsApi.createNamespacedCustomObject(V1alpha3EnvoyFilter.API_GROUP,
            V1alpha3EnvoyFilter.VERSION, controllerNamespace, V1alpha3EnvoyFilter.PLURAL, filter, null, null, null);
        return client.getJSON().deserialize(client.getJSON().serialize(response), V1alpha3EnvoyFilter.class);
    }

    /**
     * 替换EnvoyFilter资源
     * @param filter EnvoyFilter对象
     * @return 替换后的EnvoyFilter对象
     * @throws ApiException API异常
     */
    public V1alpha3EnvoyFilter replaceEnvoyFilter(V1alpha3EnvoyFilter filter) throws ApiException {
        V1ObjectMeta metadata = filter.getMetadata();
        if (metadata == null) {
            throw new IllegalArgumentException("EnvoyFilter doesn't have a valid metadata.");
        }
        renderDefaultMetadata(filter);
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        Object response =
            customObjectsApi.replaceNamespacedCustomObject(V1alpha3EnvoyFilter.API_GROUP, V1alpha3EnvoyFilter.VERSION,
                controllerNamespace, V1alpha3EnvoyFilter.PLURAL, metadata.getName(), filter, null, null);
        return client.getJSON().deserialize(client.getJSON().serialize(response), V1alpha3EnvoyFilter.class);
    }

    /**
     * 删除EnvoyFilter资源
     * @param name EnvoyFilter名称
     * @throws ApiException API异常
     */
    public void deleteEnvoyFilter(String name) throws ApiException {
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        try {
            customObjectsApi.deleteNamespacedCustomObject(V1alpha3EnvoyFilter.API_GROUP, V1alpha3EnvoyFilter.VERSION,
                controllerNamespace, V1alpha3EnvoyFilter.PLURAL, name, null, null, null, null, null);
        } catch (ApiException e) {
            if (e.getCode() != HttpStatus.NOT_FOUND) {
                throw e;
            }
        }
    }

    /**
     * 读取EnvoyFilter资源
     * @param name EnvoyFilter名称
     * @return EnvoyFilter对象
     * @throws ApiException API异常
     */
    public V1alpha3EnvoyFilter readEnvoyFilter(String name) throws ApiException {
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
        try {
            Object response = customObjectsApi.getNamespacedCustomObject(V1alpha3EnvoyFilter.API_GROUP,
                V1alpha3EnvoyFilter.VERSION, controllerNamespace, V1alpha3EnvoyFilter.PLURAL, name);
            return client.getJSON().deserialize(client.getJSON().serialize(response), V1alpha3EnvoyFilter.class);
        } catch (ApiException e) {
            if (e.getCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 检查响应状态
     * @param status 响应状态
     */
    private void checkResponseStatus(V1Status status) {
        // TODO: 根据状态抛出相应异常
    }

    /**
     * 构建控制器请求
     * @param path 请求路径
     * @return 请求对象
     * @throws IOException IO异常
     */
    private Request buildControllerRequest(String path) throws IOException {
        String serviceHost = inClusterMode ? controllerServiceName + "." + controllerNamespace : controllerServiceHost;
        String url = "http://" + serviceHost + ":" + controllerServicePort + path;
        Request.Builder builder = new Request.Builder().url(url);
        String token = controllerAccessToken;
        if (Strings.isNullOrEmpty(token) && inClusterMode) {
            token = readTokenFromFile();
        }
        if (!Strings.isNullOrEmpty(token)) {
            builder.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return builder.build();
    }

    /**
     * 从文件读取令牌
     * @return 令牌字符串
     * @throws IOException IO异常
     */
    private String readTokenFromFile() throws IOException {
        String fileName = CONTROLLER_ACCESS_TOKEN_FILE_PATH;
        if (KubernetesConstants.JwtPolicy.FIRST_PARTY_JWT.equals(controllerJwtPolicy)) {
            fileName = POD_SERVICE_ACCOUNT_TOKEN_FILE_PATH;
        }
        return FileUtils.readFileToString(new File(fileName), Charset.defaultCharset());
    }

    /**
     * 渲染默认元数据
     * @param object Kubernetes对象
     */
    private void renderDefaultMetadata(KubernetesObject object) {
        KubernetesUtil.setLabel(object, Label.RESOURCE_DEFINER_KEY, Label.RESOURCE_DEFINER_VALUE);
        if (KubernetesUtil.isInternalResource(object)) {
            KubernetesUtil.setLabel(object, Label.INTERNAL_KEY, Boolean.toString(true));
            KubernetesUtil.setAnnotation(object, Annotation.COMMENT_KEY, HigressConstants.INTERNAL_RESOURCE_COMMENT);
        }
    }

    /**
     * 保留被监听的Ingress
     * @param ingresses Ingress列表
     */
    private void retainWatchedIngress(List<V1Ingress> ingresses) {
        if (CollectionUtils.isNotEmpty(ingresses)) {
            ingresses.removeIf(i -> !isIngressWatched.test(i));
        }
    }

    /**
     * 填充默认Ingress类
     * @param ingress Ingress对象
     */
    private void fillDefaultIngressClass(V1Ingress ingress) {
        V1IngressSpec spec = Objects.requireNonNull(ingress.getSpec());
        if (StringUtils.isEmpty(spec.getIngressClassName())) {
            spec.setIngressClassName(defaultIngressClass);
        }
    }

    /**
     * 构建Ingress监听谓词
     * @param controllerWatchedIngressClassName 控制器监听的Ingress类名
     * @return 谓词函数
     */
    private static Predicate<V1Ingress> buildIsIngressWatchedPredicate(String controllerWatchedIngressClassName) {
        if (StringUtils.isEmpty(controllerWatchedIngressClassName)) {
            return ingress -> true;
        }
        if (HigressConstants.NGINX_INGRESS_CLASS_NAME.equals(controllerWatchedIngressClassName)) {
            return ingress -> {
                String ingressClass = getIngressClassName(ingress);
                return StringUtils.isEmpty(ingressClass)
                    || HigressConstants.NGINX_INGRESS_CLASS_NAME.equals(ingressClass);
            };
        }
        return ingress -> controllerWatchedIngressClassName.equals(getIngressClassName(ingress));
    }

    /**
     * 获取Ingress类名
     * @param ingress Ingress对象
     * @return Ingress类名
     */
    private static String getIngressClassName(V1Ingress ingress) {
        V1IngressSpec spec = ingress.getSpec();
        if (spec == null) {
            return null;
        }
        return spec.getIngressClassName();
    }

    /**
     * 对Kubernetes对象进行排序
     * @param objects Kubernetes对象列表
     * @param <T> Kubernetes对象类型
     * @return 排序后的对象列表
     */
    private static <T extends KubernetesObject> List<T> sortKubernetesObjects(List<T> objects) {
        if (CollectionUtils.isNotEmpty(objects)) {
            objects.sort(Comparator.comparing(o -> o.getMetadata() != null ? o.getMetadata().getName() : null));
        }
        return objects;
    }

    /**
     * 验证配置
     * @param config Higress服务配置
     */
    private static void validateConfig(HigressServiceConfig config) {
        if (isInCluster()) {
            if (StringUtils.isEmpty(config.getControllerServiceName())) {
                throw new IllegalArgumentException("controllerServiceName is required");
            }
        } else {
            if (StringUtils.isEmpty(config.getControllerServiceHost())) {
                throw new IllegalArgumentException("controllerServiceHost is required");
            }
        }
        if (StringUtils.isEmpty(config.getControllerNamespace())) {
            throw new IllegalArgumentException("controllerNamespace is required");
        }
        if (config.getControllerServicePort() == null) {
            throw new IllegalArgumentException("controllerServicePort is required");
        } else if (config.getControllerServicePort() <= 0 || config.getControllerServicePort() > 65535) {
            throw new IllegalArgumentException("controllerServicePort is invalid");
        }
        if (StringUtils.isEmpty(config.getControllerJwtPolicy())) {
            throw new IllegalArgumentException("controllerJwtPolicy is required");
        }
    }
}
