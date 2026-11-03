package com.vitzemtsov.api_gateway.config.constants;

public final class GatewayPaths {

    private GatewayPaths() {}

    public static final String API_V1 = "/api/v1";

    public static final String ACTUATOR_HEALTH = "/actuator/health";
    public static final String ACTUATOR_INFO = "/actuator/info";
    public static final String ACTUATOR_GATEWAY = "/actuator/gateway/**";

    public static final String FILES_ALL = API_V1 + "/files/**";
}