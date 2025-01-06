package com.example.starter.csvfile;

import com.example.starter.crudmongodb.DbVerticle;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CsvVerticle extends AbstractVerticle {
    private static final Pattern CSV_PATTERN = Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
    private static final String DEFAULT_COLLECTION = "csv_data";

    @Override
    public void start(Promise<Void> startPromise) {

        vertx.deployVerticle(new DbVerticle())
            .onSuccess(id -> {
                System.out.println("DbVerticle deploye avec succes: " + id);
                setupCsvConsumer();
                startPromise.complete();
            })
            .onFailure(err -> {
                System.err.println("Erreur lors du déploiement de DbVerticle: " + err.getMessage());
                startPromise.fail(err);
            });
    }

    private void setupCsvConsumer() {
        vertx.eventBus().consumer("csv.process", message -> {
            JsonObject request = (JsonObject) message.body();
            String filePath = request.getString("filePath");
            String collection = DEFAULT_COLLECTION;

            Instant startTime = Instant.now();

            processCsvFile(filePath, collection)
                .onSuccess(result -> {
                    Instant endTime = Instant.now();
                    Duration duration = Duration.between(startTime, endTime);
                    JsonObject response = new JsonObject()
                        .put("status", "success")
                        .put("rowsInserted", result.getInteger("totalProcessed", 0))
                        .put("timeTaken", duration.toMillis() + " ms");
                    message.reply(response);
                })
                .onFailure(err -> message.fail(500, err.getMessage()));
        });
    }

    private Future<JsonObject> processCsvFile(String filePath, String collection) {
        Promise<JsonObject> promise = Promise.promise();
        AtomicInteger totalProcessed = new AtomicInteger(0);

        vertx.fileSystem().readFile(filePath)
            .onSuccess(buffer -> {
                String[] lines = buffer.toString().split("\n");
                if (lines.length == 0) {
                    promise.fail("Empty file");
                    return;
                }

                String headerLine = lines[0].trim();
                String[] headers = CSV_PATTERN.split(headerLine);
                System.out.println("Using primary key: " + headers[0]);

                JsonObject findRequest = new JsonObject()
                    .put("collection", collection)
                    .put("query", new JsonObject());

                vertx.eventBus().request("db.find", findRequest, reply -> {
                    if (reply.succeeded()) {
                        processLine(lines, 1, headers, collection, totalProcessed, promise);
                    } else {
                        promise.fail(reply.cause());
                    }
                });
            })
            .onFailure(promise::fail);

        return promise.future();
    }

    private void processLine(String[] lines, int currentIndex, String[] headers,
                           String collection, AtomicInteger totalProcessed,
                           Promise<JsonObject> promise) {
        if (currentIndex >= lines.length) {
            promise.complete(new JsonObject()
                .put("totalProcessed", totalProcessed.get())
                .put("status", "success"));
            return;
        }

        String line = lines[currentIndex];
        if (line.trim().isEmpty()) {
            processLine(lines, currentIndex + 1, headers, collection, totalProcessed, promise);
            return;
        }

        try {
            JsonObject newDocument = createDocument(line, headers);
            if (newDocument != null) {
                String primaryKeyField = headers[0];
                String primaryKeyValue = newDocument.getString(primaryKeyField);
                JsonObject query = new JsonObject().put(primaryKeyField, primaryKeyValue);

                JsonObject findRequest = new JsonObject()
                    .put("collection", collection)
                    .put("query", query);

                vertx.eventBus().request("db.find", findRequest, findReply -> {
                    if (findReply.succeeded()) {
                        JsonArray results = (JsonArray) findReply.result().body();
                        if (results.isEmpty()) {
                            JsonObject insertRequest = new JsonObject()
                                .put("collection", collection)
                                .put("document", newDocument);

                            vertx.eventBus().request("db.insert", insertRequest, insertReply -> {
                                if (insertReply.succeeded()) {
                                    totalProcessed.incrementAndGet();
                                    System.out.printf("Ligne %d : Document insert avec success. %s = %s%n",
                                        currentIndex, primaryKeyField, primaryKeyValue);
                                } else {
                                    System.err.printf("Erreur d'insertion ligne %d : %s%n",
                                        currentIndex, insertReply.cause().getMessage());
                                }
                                processLine(lines, currentIndex + 1, headers, collection,
                                    totalProcessed, promise);
                            });
                        } else {
                            JsonObject existingDocument = results.getJsonObject(0);
                            if (documentsAreDifferent(existingDocument, newDocument)) {
                                String id = existingDocument.getString("_id");
                                JsonObject updateRequest = new JsonObject()
                                    .put("collection", collection)
                                    .put("id", id)
                                    .put("update", newDocument);

                                vertx.eventBus().request("db.update", updateRequest, updateReply -> {
                                    if (updateReply.succeeded()) {
                                        totalProcessed.incrementAndGet();
                                        System.out.printf("Ligne %d : Document mis a jour avec success. %s = %s%n",
                                            currentIndex, primaryKeyField, primaryKeyValue);
                                    } else {
                                        System.err.printf("Erreur de mise à jour ligne %d : %s%n",
                                            currentIndex, updateReply.cause().getMessage());
                                    }
                                    processLine(lines, currentIndex + 1, headers, collection,
                                        totalProcessed, promise);
                                });
                            } else {
                                System.out.printf("Ligne %d : Document existant avec %s = %s%n",
                                    currentIndex, primaryKeyField, primaryKeyValue);
                                processLine(lines, currentIndex + 1, headers, collection,
                                    totalProcessed, promise);
                            }
                        }
                    } else {
                        System.err.printf("Erreur de recherche ligne %d : %s%n",
                            currentIndex, findReply.cause().getMessage());
                        processLine(lines, currentIndex + 1, headers, collection,
                            totalProcessed, promise);
                    }
                });
            } else {
                System.err.printf("Format de document invalide à la ligne %d%n", currentIndex);
                processLine(lines, currentIndex + 1, headers, collection, totalProcessed, promise);
            }
        } catch (Exception e) {
            System.err.printf("Erreur de traitement ligne %d : %s%n", currentIndex, e.getMessage());
            processLine(lines, currentIndex + 1, headers, collection, totalProcessed, promise);
        }
    }

    private boolean documentsAreDifferent(JsonObject doc1, JsonObject doc2) {
        for (String fieldName : doc2.fieldNames()) {
            if (!doc1.containsKey(fieldName) || !doc1.getValue(fieldName).equals(doc2.getValue(fieldName))) {
                return true;
            }
        }
        return false;
    }

    private JsonObject createDocument(String line, String[] headers) {
        String[] values = CSV_PATTERN.split(line.trim());
        if (values.length != headers.length) return null;

        JsonObject document = new JsonObject();
        for (int i = 0; i < headers.length; i++) {
            String value = cleanValue(values[i]);
            String header = cleanValue(headers[i]);

            if (value.matches("^\\d+$")) {
                document.put(header, Long.valueOf(value));
            } else if (value.matches("^\\d*\\.\\d+$")) {
                document.put(header, Double.valueOf(value));
            } else {
                document.put(header, value);
            }
        }
        return document;
    }

    private String cleanValue(String value) {
        value = value.replaceAll("^\"|\"$", "").trim();
        value = value.replaceAll("[\\\\;{}()\\[\\]\"']", "");
        return value;
    }
}
