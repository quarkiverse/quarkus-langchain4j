package io.quarkiverse.langchain4j.watsonx.bean;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Error")
@XmlAccessorType(XmlAccessType.FIELD)
public class CosError {
    @XmlElement(name = "Code")
    private Code code;
    @XmlElement(name = "Message")
    private String message;
    @XmlElement(name = "Resource")
    private String resource;
    @XmlElement(name = "RequestId")
    private String requestId;
    @XmlElement(name = "httpStatusCode")
    private int httpStatusCode;

    public Code getCode() {
        return code;
    }

    public void setCode(Code code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(int httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((code == null) ? 0 : code.hashCode());
        result = prime * result + ((message == null) ? 0 : message.hashCode());
        result = prime * result + ((resource == null) ? 0 : resource.hashCode());
        result = prime * result + ((requestId == null) ? 0 : requestId.hashCode());
        result = prime * result + httpStatusCode;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CosError other = (CosError) obj;
        if (code != other.code)
            return false;
        if (message == null) {
            if (other.message != null)
                return false;
        } else if (!message.equals(other.message))
            return false;
        if (resource == null) {
            if (other.resource != null)
                return false;
        } else if (!resource.equals(other.resource))
            return false;
        if (requestId == null) {
            if (other.requestId != null)
                return false;
        } else if (!requestId.equals(other.requestId))
            return false;
        if (httpStatusCode != other.httpStatusCode)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "CosError [code=" + code + ", message=" + message + ", resource=" + resource + ", requestId=" + requestId
                + ", httpStatusCode=" + httpStatusCode + "]";
    }

    @XmlEnum
    public static enum Code {

        @XmlEnumValue("AccessDenied")
        ACCESS_DENIED,

        @XmlEnumValue("BadDigest")
        BAD_DIGEST,

        @XmlEnumValue("BucketAlreadyExists")
        BUCKET_ALREADY_EXISTS,

        @XmlEnumValue("BucketAlreadyOwnedByYou")
        BUCKET_ALREADY_OWNED_BY_YOU,

        @XmlEnumValue("BucketNotEmpty")
        BUCKET_NOT_EMPTY,

        @XmlEnumValue("CredentialsNotSupported")
        CREDENTIALS_NOT_SUPPORTED,

        @XmlEnumValue("EntityTooSmall")
        ENTITY_TOO_SMALL,

        @XmlEnumValue("EntityTooLarge")
        ENTITY_TOO_LARGE,

        @XmlEnumValue("IncompleteBody")
        INCOMPLETE_BODY,

        @XmlEnumValue("IncorrectNumberOfFilesInPostRequest")
        INCORRECT_NUMBER_OF_FILES_IN_POST_REQUEST,

        @XmlEnumValue("InlineDataTooLarge")
        INLINE_DATA_TOO_LARGE,

        @XmlEnumValue("InternalError")
        INTERNAL_ERROR,

        @XmlEnumValue("InvalidAccessKeyId")
        INVALID_ACCESS_KEY_ID,

        @XmlEnumValue("InvalidArgument")
        INVALID_ARGUMENT,

        @XmlEnumValue("InvalidBucketName")
        INVALID_BUCKET_NAME,

        @XmlEnumValue("InvalidBucketState")
        INVALID_BUCKET_STATE,

        @XmlEnumValue("InvalidDigest")
        INVALID_DIGEST,

        @XmlEnumValue("InvalidLocationConstraint")
        INVALID_LOCATION_CONSTRAINT,

        @XmlEnumValue("InvalidObjectState")
        INVALID_OBJECT_STATE,

        @XmlEnumValue("InvalidPart")
        INVALID_PART,

        @XmlEnumValue("InvalidPartOrder")
        INVALID_PART_ORDER,

        @XmlEnumValue("InvalidRange")
        INVALID_RANGE,

        @XmlEnumValue("InvalidRequest")
        INVALID_REQUEST,

        @XmlEnumValue("InvalidSecurity")
        INVALID_SECURITY,

        @XmlEnumValue("InvalidURI")
        INVALID_URI,

        @XmlEnumValue("KeyTooLong")
        KEY_TOO_LONG,

        @XmlEnumValue("MalformedPOSTRequest")
        MALFORMED_POST_REQUEST,

        @XmlEnumValue("MalformedXML")
        MALFORMED_XML,

        @XmlEnumValue("MaxMessageLengthExceeded")
        MAX_MESSAGE_LENGTH_EXCEEDED,

        @XmlEnumValue("MaxPostPreDataLengthExceededError")
        MAX_POST_PRE_DATA_LENGTH_EXCEEDED_ERROR,

        @XmlEnumValue("MetadataTooLarge")
        METADATA_TOO_LARGE,

        @XmlEnumValue("MethodNotAllowed")
        METHOD_NOT_ALLOWED,

        @XmlEnumValue("MissingContentLength")
        MISSING_CONTENT_LENGTH,

        @XmlEnumValue("MissingRequestBodyError")
        MISSING_REQUEST_BODY_ERROR,

        @XmlEnumValue("NoSuchBucket")
        NO_SUCH_BUCKET,

        @XmlEnumValue("NoSuchKey")
        NO_SUCH_KEY,

        @XmlEnumValue("NoSuchUpload")
        NO_SUCH_UPLOAD,

        @XmlEnumValue("NotImplemented")
        NOT_IMPLEMENTED,

        @XmlEnumValue("OperationAborted")
        OPERATION_ABORTED,

        @XmlEnumValue("PreconditionFailed")
        PRECONDITION_FAILED,

        @XmlEnumValue("Redirect")
        REDIRECT,

        @XmlEnumValue("RequestIsNotMultiPartContent")
        REQUEST_IS_NOT_MULTIPART_CONTENT,

        @XmlEnumValue("RequestTimeout")
        REQUEST_TIMEOUT,

        @XmlEnumValue("RequestTimeTooSkewed")
        REQUEST_TIME_TOO_SKEWED,

        @XmlEnumValue("ServiceUnavailable")
        SERVICE_UNAVAILABLE,

        @XmlEnumValue("SlowDown")
        SLOW_DOWN,

        @XmlEnumValue("TemporaryRedirect")
        TEMPORARY_REDIRECT,

        @XmlEnumValue("TooManyBuckets")
        TOO_MANY_BUCKETS,

        @XmlEnumValue("UnexpectedContent")
        UNEXPECTED_CONTENT,

        @XmlEnumValue("UserKeyMustBeSpecified")
        USER_KEY_MUST_BE_SPECIFIED;
    }
}
