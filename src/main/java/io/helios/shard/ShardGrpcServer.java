package io.helios.shard;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.helios.grpc.ApplyRequest;
import io.helios.grpc.ApplyResponse;
import io.helios.grpc.Candidate;
import io.helios.grpc.HealthRequest;
import io.helios.grpc.HealthResponse;
import io.helios.grpc.IndexShardGrpc;
import io.helios.grpc.PersistRequest;
import io.helios.grpc.PersistResponse;
import io.helios.grpc.RemoveRequest;
import io.helios.grpc.RemoveResponse;
import io.helios.grpc.SearchRequest;
import io.helios.grpc.SearchResponse;
import io.helios.index.CandidateSet;

import java.io.IOException;
import java.util.UUID;

public final class ShardGrpcServer {
    private final ShardService svc;
    private Server server;

    public ShardGrpcServer(ShardService svc) {
        this.svc = svc;
    }

    public void start(int port) throws IOException {
        server = ServerBuilder.forPort(port).addService(new Service()).build().start();
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private final class Service extends IndexShardGrpc.IndexShardImplBase {
        @Override
        public void search(SearchRequest req, StreamObserver<SearchResponse> obs) {
            CandidateSet set = svc.search(req.getQuery(), req.getLimit(), req.getPrimaryId());
            SearchResponse.Builder b = SearchResponse.newBuilder()
                    .setPassageCount(set.passageCount())
                    .setSumLengths(set.sumLengths());
            set.df().forEach(b::putDf);
            for (io.helios.index.Candidate c : set.candidates()) {
                Candidate.Builder cb = Candidate.newBuilder()
                        .setPassageId(c.passageId().toString())
                        .setLength(c.length())
                        .addAllMatchedTerms(c.matchedTerms());
                c.tf().forEach(cb::putTf);
                b.addCandidates(cb);
            }
            obs.onNext(b.build());
            obs.onCompleted();
        }

        @Override
        public void apply(ApplyRequest req, StreamObserver<ApplyResponse> obs) {
            svc.apply(UUID.fromString(req.getPassageId()), UUID.fromString(req.getDocumentId()), req.getText());
            obs.onNext(ApplyResponse.getDefaultInstance());
            obs.onCompleted();
        }

        @Override
        public void remove(RemoveRequest req, StreamObserver<RemoveResponse> obs) {
            svc.remove(UUID.fromString(req.getPassageId()));
            obs.onNext(RemoveResponse.getDefaultInstance());
            obs.onCompleted();
        }

        @Override
        public void persist(PersistRequest req, StreamObserver<PersistResponse> obs) {
            svc.persist();
            obs.onNext(PersistResponse.getDefaultInstance());
            obs.onCompleted();
        }

        @Override
        public void health(HealthRequest req, StreamObserver<HealthResponse> obs) {
            obs.onNext(HealthResponse.newBuilder()
                    .setShardId(svc.id())
                    .setReady(true)
                    .setPassageCount(svc.stats().passageCount())
                    .setPrimaryCount(svc.primaryCount())
                    .build());
            obs.onCompleted();
        }
    }
}
