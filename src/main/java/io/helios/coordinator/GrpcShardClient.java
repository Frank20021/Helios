package io.helios.coordinator;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.helios.grpc.ApplyRequest;
import io.helios.grpc.HealthRequest;
import io.helios.grpc.HealthResponse;
import io.helios.grpc.IndexShardGrpc;
import io.helios.grpc.PersistRequest;
import io.helios.grpc.RemoveRequest;
import io.helios.grpc.SearchRequest;
import io.helios.grpc.SearchResponse;
import io.helios.hashring.HashRing;
import io.helios.index.Candidate;
import io.helios.index.CandidateSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class GrpcShardClient implements ShardClient {
    private final String id;
    private final ManagedChannel channel;
    private final IndexShardGrpc.IndexShardBlockingStub stub;

    public GrpcShardClient(HashRing.Node node) {
        this.id = node.id();
        this.channel = ManagedChannelBuilder.forTarget(node.addr()).usePlaintext().build();
        this.stub = IndexShardGrpc.newBlockingStub(channel);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public CandidateSet search(String query, int limit, String primaryId) {
        SearchResponse res = stub.search(SearchRequest.newBuilder()
                .setQuery(query).setLimit(limit).setPrimaryId(primaryId == null ? "" : primaryId).build());
        Map<String, Integer> df = new HashMap<>();
        res.getDfMap().forEach((k, v) -> df.put(k, v));
        List<Candidate> cands = new ArrayList<>();
        res.getCandidatesList().forEach(c -> {
            try {
                Map<String, Integer> tf = new HashMap<>();
                c.getTfMap().forEach((k, v) -> tf.put(k, v));
                cands.add(new Candidate(UUID.fromString(c.getPassageId()), c.getLength(), tf, c.getMatchedTermsList()));
            } catch (IllegalArgumentException ignored) {
            }
        });
        return new CandidateSet((int) res.getPassageCount(), (int) res.getSumLengths(), df, cands);
    }

    @Override
    public void apply(UUID passageId, UUID documentId, String text) {
        stub.apply(ApplyRequest.newBuilder()
                .setPassageId(passageId.toString())
                .setDocumentId(documentId.toString())
                .setText(text)
                .build());
    }

    @Override
    public void remove(UUID passageId) {
        stub.remove(RemoveRequest.newBuilder().setPassageId(passageId.toString()).build());
    }

    @Override
    public void persist() {
        stub.persist(PersistRequest.getDefaultInstance());
    }

    @Override
    public ShardHealth health() {
        HealthResponse res = stub.health(HealthRequest.getDefaultInstance());
        return new ShardHealth(res.getShardId(), res.getReady(), (int) res.getPassageCount(), (int) res.getPrimaryCount());
    }

    @Override
    public void close() {
        channel.shutdownNow();
        try {
            channel.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
