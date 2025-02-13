package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.quarkiverse.langchain4j.audit.AuditSourceInfo;

/**
 * Contains information about the source of an audit event
 */
final class AuditSourceInfoImpl implements AuditSourceInfo {
    private final UUID interactionId = UUID.randomUUID();
    private final String interfaceName;
    private final String methodName;
    private final Optional<Integer> memoryIDParamPosition;
    private final Object[] methodParams;

    public AuditSourceInfoImpl(String interfaceName, String methodName, Optional<Integer> memoryIDParamPosition,
            Object... methodParams) {
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.memoryIDParamPosition = memoryIDParamPosition;
        this.methodParams = methodParams;
    }

    public AuditSourceInfoImpl(AiServiceMethodCreateInfo createInfo, Object... methodParams) {
        this(createInfo.getInterfaceName(), createInfo.getMethodName(),
                createInfo.getMemoryIdParamPosition(), methodParams);
    }

    @Override
    public String interfaceName() {
        return interfaceName;
    }

    @Override
    public String methodName() {
        return methodName;
    }

    @Override
    public Optional<Integer> memoryIDParamPosition() {
        return memoryIDParamPosition;
    }

    @Override
    public Object[] methodParams() {
        return methodParams;
    }

    @Override
    public UUID interactionId() {
        return interactionId;
    }

    @Override
    public String toString() {
        return "AuditSourceInfo{" + "interactionId=" + interactionId +
                ", interfaceName='" + interfaceName +
                '\'' + ", methodName='" + methodName +
                '\'' + ", memoryIDParamPosition=" + memoryIDParamPosition +
                ", methodParams=" + Arrays.toString(methodParams) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AuditSourceInfoImpl that = (AuditSourceInfoImpl) o;
        return Objects.equals(interactionId, that.interactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(interactionId);
    }
}
