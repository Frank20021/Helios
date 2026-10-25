package io.helios.grpc;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;

public final class IndexShardGrpc {
    public static final String SERVICE_NAME = "helios.v1.IndexShard";

    private IndexShardGrpc() {
    }

    private static volatile MethodDescriptor<SearchRequest, SearchResponse> searchMethod;
    private static volatile MethodDescriptor<ApplyRequest, ApplyResponse> applyMethod;
    private static volatile MethodDescriptor<RemoveRequest, RemoveResponse> removeMethod;
    private static volatile MethodDescriptor<PersistRequest, PersistResponse> persistMethod;
    private static volatile MethodDescriptor<HealthRequest, HealthResponse> healthMethod;

    public static MethodDescriptor<SearchRequest, SearchResponse> getSearchMethod() {
        MethodDescriptor<SearchRequest, SearchResponse> m = searchMethod;
        if (m == null) {
            synchronized (IndexShardGrpc.class) {
                m = searchMethod;
                if (m == null) {
                    searchMethod = m = unary("Search", SearchRequest.getDefaultInstance(), SearchResponse.getDefaultInstance());
                }
            }
        }
        return m;
    }

    public static MethodDescriptor<ApplyRequest, ApplyResponse> getApplyMethod() {
        MethodDescriptor<ApplyRequest, ApplyResponse> m = applyMethod;
        if (m == null) {
            synchronized (IndexShardGrpc.class) {
                m = applyMethod;
                if (m == null) {
                    applyMethod = m = unary("Apply", ApplyRequest.getDefaultInstance(), ApplyResponse.getDefaultInstance());
                }
            }
        }
        return m;
    }

    public static MethodDescriptor<RemoveRequest, RemoveResponse> getRemoveMethod() {
        MethodDescriptor<RemoveRequest, RemoveResponse> m = removeMethod;
        if (m == null) {
            synchronized (IndexShardGrpc.class) {
                m = removeMethod;
                if (m == null) {
                    removeMethod = m = unary("Remove", RemoveRequest.getDefaultInstance(), RemoveResponse.getDefaultInstance());
                }
            }
        }
        return m;
    }

    public static MethodDescriptor<PersistRequest, PersistResponse> getPersistMethod() {
        MethodDescriptor<PersistRequest, PersistResponse> m = persistMethod;
        if (m == null) {
            synchronized (IndexShardGrpc.class) {
                m = persistMethod;
                if (m == null) {
                    persistMethod = m = unary("Persist", PersistRequest.getDefaultInstance(), PersistResponse.getDefaultInstance());
                }
            }
        }
        return m;
    }

    public static MethodDescriptor<HealthRequest, HealthResponse> getHealthMethod() {
        MethodDescriptor<HealthRequest, HealthResponse> m = healthMethod;
        if (m == null) {
            synchronized (IndexShardGrpc.class) {
                m = healthMethod;
                if (m == null) {
                    healthMethod = m = unary("Health", HealthRequest.getDefaultInstance(), HealthResponse.getDefaultInstance());
                }
            }
        }
        return m;
    }

    public static IndexShardBlockingStub newBlockingStub(io.grpc.Channel channel) {
        return new IndexShardBlockingStub(channel, io.grpc.CallOptions.DEFAULT);
    }

    public abstract static class IndexShardImplBase implements BindableService {
        public void search(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
            asyncUnimplemented(getSearchMethod(), responseObserver);
        }

        public void apply(ApplyRequest request, StreamObserver<ApplyResponse> responseObserver) {
            asyncUnimplemented(getApplyMethod(), responseObserver);
        }

        public void remove(RemoveRequest request, StreamObserver<RemoveResponse> responseObserver) {
            asyncUnimplemented(getRemoveMethod(), responseObserver);
        }

        public void persist(PersistRequest request, StreamObserver<PersistResponse> responseObserver) {
            asyncUnimplemented(getPersistMethod(), responseObserver);
        }

        public void health(HealthRequest request, StreamObserver<HealthResponse> responseObserver) {
            asyncUnimplemented(getHealthMethod(), responseObserver);
        }

        @Override
        public final ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(serviceDescriptor())
                    .addMethod(getSearchMethod(), ServerCalls.asyncUnaryCall((req, obs) -> search(req, obs)))
                    .addMethod(getApplyMethod(), ServerCalls.asyncUnaryCall((req, obs) -> apply(req, obs)))
                    .addMethod(getRemoveMethod(), ServerCalls.asyncUnaryCall((req, obs) -> remove(req, obs)))
                    .addMethod(getPersistMethod(), ServerCalls.asyncUnaryCall((req, obs) -> persist(req, obs)))
                    .addMethod(getHealthMethod(), ServerCalls.asyncUnaryCall((req, obs) -> health(req, obs)))
                    .build();
        }
    }

    public static final class IndexShardBlockingStub extends AbstractBlockingStub<IndexShardBlockingStub> {
        private IndexShardBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions options) {
            super(channel, options);
        }

        @Override
        protected IndexShardBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions options) {
            return new IndexShardBlockingStub(channel, options);
        }

        public SearchResponse search(SearchRequest request) {
            return ClientCalls.blockingUnaryCall(getChannel(), getSearchMethod(), getCallOptions(), request);
        }

        public ApplyResponse apply(ApplyRequest request) {
            return ClientCalls.blockingUnaryCall(getChannel(), getApplyMethod(), getCallOptions(), request);
        }

        public RemoveResponse remove(RemoveRequest request) {
            return ClientCalls.blockingUnaryCall(getChannel(), getRemoveMethod(), getCallOptions(), request);
        }

        public PersistResponse persist(PersistRequest request) {
            return ClientCalls.blockingUnaryCall(getChannel(), getPersistMethod(), getCallOptions(), request);
        }

        public HealthResponse health(HealthRequest request) {
            return ClientCalls.blockingUnaryCall(getChannel(), getHealthMethod(), getCallOptions(), request);
        }
    }

    @SuppressWarnings("unchecked")
    private static <Req extends com.google.protobuf.Message, Res extends com.google.protobuf.Message>
    MethodDescriptor<Req, Res> unary(String name, Req req, Res res) {
        return MethodDescriptor.<Req, Res>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, name))
                .setRequestMarshaller(ProtoUtils.marshaller(req))
                .setResponseMarshaller(ProtoUtils.marshaller(res))
                .build();
    }

    private static ServiceDescriptor serviceDescriptor() {
        return ServiceDescriptor.newBuilder(SERVICE_NAME)
                .addMethod(getSearchMethod())
                .addMethod(getApplyMethod())
                .addMethod(getRemoveMethod())
                .addMethod(getPersistMethod())
                .addMethod(getHealthMethod())
                .build();
    }

    private static <T> void asyncUnimplemented(MethodDescriptor<?, ?> method, StreamObserver<T> obs) {
        obs.onError(io.grpc.Status.UNIMPLEMENTED.withDescription(method.getFullMethodName()).asRuntimeException());
    }
}
