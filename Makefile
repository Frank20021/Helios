.PHONY: test test-integration run run-shard eval bench lint fmt postgres

MVN ?= mvn

test:
	$(MVN) -q test

test-integration:
	HELIOS_REQUIRE_DB=1 $(MVN) -q test

run:
	$(MVN) -pl . spring-boot:run

run-shard:
	$(MVN) -Dstart-class=io.helios.shard.HeliosShardApplication spring-boot:run

eval:
	$(MVN) -q -DskipTests exec:java -Dexec.mainClass=io.helios.eval.EvalMain

bench:
	$(MVN) -q -DskipTests exec:java -Dexec.mainClass=io.helios.eval.BenchMain -Dexec.args="-n 50"

lint:
	$(MVN) -q -DskipTests compile

fmt:
	$(MVN) -q -DskipTests compile

postgres:
	docker compose up -d postgres
