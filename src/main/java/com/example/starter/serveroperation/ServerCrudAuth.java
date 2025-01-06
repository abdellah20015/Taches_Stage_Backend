package com.example.starter.serveroperation;



import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.example.starter.authentification.AuthServicesVerticle;
import com.example.starter.config.MongoConfig;
import com.example.starter.crudmongodb.DbVerticle;
import com.example.starter.crudmongodb.UsersServicesVerticle;
import com.example.starter.csvfile.CsvVerticle;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.openapi.router.RequestExtractor;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.openapi.contract.OpenAPIContract;






public class ServerCrudAuth extends AbstractVerticle {
  private MongoAuth authProvider;
  private MongoClient mongoClient;
  @Override
  public void start(Promise<Void> startPromise) {
    vertx.deployVerticle(new AuthServicesVerticle(), authDeployment -> {
        if (authDeployment.failed()) {
            startPromise.fail(authDeployment.cause());
            return;
        }

        vertx.deployVerticle(new DbVerticle(), dbDeployment -> {
            if (dbDeployment.failed()) {
                startPromise.fail(dbDeployment.cause());
                return;
            }

            vertx.deployVerticle(new UsersServicesVerticle(), userServiceDeployment -> {
                if (userServiceDeployment.failed()) {
                    startPromise.fail(userServiceDeployment.cause());
                    return;
                }

                vertx.deployVerticle(new CsvVerticle(), csvDeployment -> {
                    if (csvDeployment.failed()) {
                        startPromise.fail(csvDeployment.cause());
                        return;
                    }


                    setupHttpServer(startPromise);
                });
            });
        });
    });
}


private void setupHttpServer(Promise<Void> startPromise) {
  String pathToContract = "src/main/resources/api/openapi.json";


  mongoClient = MongoConfig.createMongoClient(vertx);
  authProvider = MongoAuth.create(mongoClient, new JsonObject());

  OpenAPIContract.from(vertx, pathToContract)
      .onSuccess((OpenAPIContract contract) -> {
          RouterBuilder routerBuilder = RouterBuilder.create(vertx, contract, RequestExtractor.withBodyHandler());

          // Configure Session Handler
          routerBuilder.rootHandler(SessionHandler.create(LocalSessionStore.create(vertx)));

          // Configure CORS
          routerBuilder.rootHandler(CorsHandler.create()
                  .addOrigin("*")
                  .allowedMethod(HttpMethod.PUT)
                  .allowedMethod(HttpMethod.GET)
                  .allowedMethod(HttpMethod.POST)
                  .allowedMethod(HttpMethod.DELETE)
                  .allowedMethod(HttpMethod.OPTIONS)
                  .allowedHeader("Content-Type")
                  .allowCredentials(true)
          );
          // Configure Body Handler
          routerBuilder.rootHandler(BodyHandler.create()
             .setUploadsDirectory("uploads")
             .setDeleteUploadedFilesOnEnd(false)
             .setMergeFormAttributes(true)
             .setBodyLimit(1024L * 1024L * 1024L)
             .setPreallocateBodyBuffer(true)
          );
          routerBuilder.rootHandler(ServerCrudAuth.this::checkPath);




          // Private routes CRUD
          routerBuilder.getRoute("createUser").addHandler(ServerCrudAuth.this::usersHandler);
          routerBuilder.getRoute("listUsers").addHandler(ServerCrudAuth.this::usersHandler);
          routerBuilder.getRoute("updateUser").addHandler(ServerCrudAuth.this::usersHandler);
          routerBuilder.getRoute("deleteUser").addHandler(ServerCrudAuth.this::usersHandler);

          // Auth routes
          routerBuilder.getRoute("userLogin").addHandler(ServerCrudAuth.this::login);
          routerBuilder.getRoute("userSignup").addHandler(ServerCrudAuth.this::signup);
          routerBuilder.getRoute("userLogout").addHandler(ServerCrudAuth.this::logout);
          routerBuilder.getRoute("checkAuth").addHandler(ServerCrudAuth.this::checkAuth);

          // files routes
          routerBuilder.getRoute("uploadFile").addHandler(ServerCrudAuth.this::uploadFile);
          routerBuilder.getRoute("listFiles").addHandler(ServerCrudAuth.this::listFiles);
          routerBuilder.getRoute("deleteFile").addHandler(ServerCrudAuth.this::deleteFile);
          //csv routes
          routerBuilder.getRoute("uploadCsv").addHandler(this::handleCsvUpload);
          //files générés routes
          routerBuilder.getRoute("generatePdf").addHandler(this::generateUserPdf);
          routerBuilder.getRoute("generateExcel").addHandler(this::generateUsersExcel);

          Router router = routerBuilder.createRouter();


          router.route("/uploads/*").handler(StaticHandler.create("uploads"));



          // Start the HTTP server
          vertx.createHttpServer()
                  .requestHandler(router)
                  .listen(8080)
                  .onComplete(http -> {
                      if (http.succeeded()) {
                          startPromise.complete();
                          System.out.println("Authentication HTTP server started on port 8080");
                      } else {
                          startPromise.fail(http.cause());
                      }
                  });
  })
      .onFailure(err -> {
          System.err.println("Failed to load OpenAPI contract: " + err.getMessage());
          startPromise.fail(err);
      });
}


public void checkPath(RoutingContext ctx) {
  String routePath = ctx.request().path();
  if (routePath.startsWith("/private/")) {
      User user = ctx.user();
      if (user != null) {
          ctx.next();
      } else {
          ctx.response()
              .setStatusCode(401)
              .putHeader("Content-Type", "application/json")
              .end(new JsonObject().put("error", "Authentication required for private routes").encode());
      }
  } else {
      ctx.next();
  }
}


    private void usersHandler(RoutingContext ctx) {
      String path = ctx.request().path();
      JsonObject data = ctx.getBodyAsJson();
      String eventBusAddress;

      switch (path) {
          case "/private/users/create" -> eventBusAddress = "user.add";
          case "/private/users/list" -> {
              eventBusAddress = "user.list";
              data = new JsonObject().put("filter", data);
            }
          case "/private/users/update" -> eventBusAddress = "user.update";
          case "/private/users/delete" -> eventBusAddress = "user.delete";
          default -> {
              ctx.response()
                      .setStatusCode(400)
                      .putHeader("Content-Type", "application/json")
                      .end(new JsonObject().put("error", "Invalid route").encode());
              return;
            }
      }

      vertx.eventBus().request(eventBusAddress, data, reply -> {
          if (reply.succeeded()) {
              JsonObject result = (JsonObject) reply.result().body();
              ctx.response()
                  .setStatusCode(200)
                  .putHeader("Content-Type", "application/json")
                  .end(result.encode());
          } else {
              int statusCode = reply.cause().getMessage().contains("not found") ? 404 : 500;
              ctx.response()
                  .setStatusCode(statusCode)
                  .putHeader("Content-Type", "application/json")
                  .end(new JsonObject()
                      .put("error", reply.cause().getMessage())
                      .encode());
          }
      });
  }
  private void signup(RoutingContext ctx) {
    String path = ctx.request().path();
    JsonObject data = ctx.getBodyAsJson();
    String eventBusAddress;

    switch (path) {
        case "/signup" -> eventBusAddress = "auth.signup";
        default -> {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Invalid route").encode());
            return;
        }
    }

    vertx.eventBus().request(eventBusAddress, data, reply -> {
        if (reply.succeeded()) {
            ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(reply.result().body().toString());
        } else {
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", reply.cause().getMessage()).encode());
        }
    });
}

private void login(RoutingContext ctx) {
  JsonObject credentials = ctx.getBodyAsJson();
  String username = credentials.getString("username");
  String password = credentials.getString("password");

  if (username == null || password == null) {
      ctx.response()
          .setStatusCode(400)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "Username and password are required").encode());
      return;
  }

  JsonObject authInfo = new JsonObject()
      .put("username", username)
      .put("password", password);

  authProvider.authenticate(authInfo, authRes -> {
      if (authRes.succeeded()) {
          User user = authRes.result();
          ctx.setUser(user);
          ctx.session().put("username", username);

          ctx.response()
              .setStatusCode(200)
              .putHeader("Content-Type", "application/json")
              .end(new JsonObject()
                  .put("message", "Login successful")
                  .put("username", username)
                  .encode());
      } else {
          ctx.response()
              .setStatusCode(401)
              .putHeader("Content-Type", "application/json")
              .end(new JsonObject().put("error", "Invalid credentials").encode());
      }
  });
}

private void logout(RoutingContext ctx) {
  ctx.session().destroy();
  ctx.clearUser();

  ctx.response()
      .setStatusCode(200)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("message", "Logged out successfully").encode());
}

private void checkAuth(RoutingContext ctx) {
  User user = ctx.user();
  if (user != null) {
    JsonObject userInfo = new JsonObject()
        .put("username", ctx.session().get("username"))
        .put("authenticated", true);

    ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(userInfo.encode());
  } else {
    ctx.response()
        .setStatusCode(401)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("authenticated", false).encode());
  }
}


private void uploadFile(RoutingContext ctx) {
  List<FileUpload> uploads = ctx.fileUploads();
  if (uploads.isEmpty()) {
      ctx.response()
          .setStatusCode(400)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "No file uploaded").encode());
      return;
  }


  JsonArray uploadResults = new JsonArray();
  uploads.forEach(fileUpload -> {
      String fileName = fileUpload.fileName();
      String[] fileParts = fileName.split("\\.");
      String fileExtension = (fileParts.length > 1) ? fileParts[fileParts.length - 1].toLowerCase() : "";

      if (fileExtension.isEmpty()) {
          uploadResults.add(new JsonObject().put("error", "File has no extension"));
          return;
      }

      String newFileName = "uploads/" + UUID.randomUUID().toString().replaceAll("[-@_()'\"]", "") + "." + fileExtension;

      FileSystem fs = vertx.fileSystem();
      fs.mkdirs("uploads", mkdirRes -> {
          if (mkdirRes.failed()) {
              uploadResults.add(new JsonObject().put("error", "Failed to create uploads directory"));
              return;
          }

          fs.move(fileUpload.uploadedFileName(), newFileName, moveRes -> {
              if (moveRes.failed()) {
                  uploadResults.add(new JsonObject().put("error", "Failed to move file"));
                  return;
              }

              String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
              JsonObject fileInfo = new JsonObject()
                  .put("filename", newFileName)
                  .put("path", newFileName)
                  .put("uploadedBy", ctx.session().get("username"))
                  .put("contentType", fileUpload.contentType())
                  .put("fileSize", fileUpload.size())
                  .put("uploadedAt", formattedDate);

              mongoClient.save("files", fileInfo, saveRes -> {
                  if (saveRes.succeeded()) {
                      uploadResults.add(new JsonObject().put("message", "File uploaded successfully"));
                  } else {
                      uploadResults.add(new JsonObject().put("error", "Failed to save file info"));
                  }
              });
          });
      });
  });

  ctx.response()
      .setStatusCode(200)
      .putHeader("Content-Type", "application/json")
      .end(uploadResults.encode());
}



private void listFiles(RoutingContext ctx) {


  JsonObject query = new JsonObject()
      .put("uploadedBy", ctx.session().get("username"));

  mongoClient.find("files", query, res -> {
      if (res.succeeded()) {
          JsonArray fileList = new JsonArray(res.result());
          ctx.response()
              .setStatusCode(200)
              .putHeader("Content-Type", "application/json")
              .end(fileList.encode());
      } else {
          ctx.response()
              .setStatusCode(500)
              .putHeader("Content-Type", "application/json")
              .end(new JsonObject().put("error", "Failed to retrieve files").encode());
      }
  });
}




private void deleteFile(RoutingContext ctx) {


  String fileId = ctx.request().getParam("fileId");
  if (fileId == null) {
      ctx.response()
          .setStatusCode(400)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "File ID is required").encode());
      return;
  }


  JsonObject query = new JsonObject()
      .put("_id", fileId)
      .put("uploadedBy", ctx.session().get("username"));

  mongoClient.findOne("files", query, null, findRes -> {
      if (findRes.succeeded() && findRes.result() != null) {
          JsonObject fileInfo = findRes.result();
          String filePath = fileInfo.getString("filename");

          vertx.fileSystem().delete(filePath, deleteFileRes -> {
              if (deleteFileRes.succeeded()) {

                  mongoClient.removeDocument("files", query, removeRes -> {
                      if (removeRes.succeeded()) {
                          ctx.response()
                              .setStatusCode(200)
                              .putHeader("Content-Type", "application/json")
                              .end(new JsonObject().put("message", "File deleted successfully").encode());
                      } else {
                          ctx.response()
                              .setStatusCode(500)
                              .putHeader("Content-Type", "application/json")
                              .end(new JsonObject().put("error", "Failed to remove file record from database").encode());
                      }
                  });
              } else {
                  ctx.response()
                      .setStatusCode(500)
                      .putHeader("Content-Type", "application/json")
                      .end(new JsonObject().put("error", "Failed to delete file from filesystem").encode());
              }
          });
      } else {
          ctx.response()
              .setStatusCode(404)
              .putHeader("Content-Type", "application/json")
              .end(new JsonObject().put("error", "File not found").encode());
      }
  });
}

private void handleCsvUpload(RoutingContext ctx) {
  List<FileUpload> uploads = ctx.fileUploads();
  if (uploads.isEmpty()) {
      ctx.response()
          .setStatusCode(400)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "No file uploaded").encode());
      return;
  }

  FileUpload csvFile = uploads.get(0);
  if (!csvFile.contentType().equals("text/csv") && !csvFile.fileName().endsWith(".csv")) {
      ctx.response()
          .setStatusCode(400)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "File must be CSV format").encode());
      return;
  }

  JsonObject request = new JsonObject()
      .put("filePath", csvFile.uploadedFileName());

  System.out.println("Debut du traitement CSV");

  vertx.eventBus().request("csv.process", request, ar -> {
      if (ar.succeeded()) {
          JsonObject result = (JsonObject) ar.result().body();
          ctx.response()
              .setStatusCode(200)
              .putHeader("Content-Type", "application/json")
              .end(result.encode());


          vertx.fileSystem().delete(csvFile.uploadedFileName(), deleteResult -> {
              if (deleteResult.failed()) {
                  System.err.println("Erreur lors de la suppression du fichier temporaire: " +
                      deleteResult.cause().getMessage());
              }
          });
      } else {
          ctx.response()
              .setStatusCode(500)
              .putHeader("Content-Type", "application/json")
              .end(new JsonObject()
                  .put("error", "Erreur lors du traitement CSV: " + ar.cause().getMessage())
                  .encode());
      }
  });
}

private void generateUserPdf(RoutingContext ctx) {
  String userId = ctx.request().getParam("userId");
  JsonObject query = new JsonObject()
      .put("collection", "users")
      .put("query", new JsonObject().put("_id", userId));

  vertx.eventBus().request("db.findOne", query, ar -> {
      if (ar.succeeded()) {
          JsonObject user = (JsonObject) ar.result().body();
          try {
              Document document = new Document();
              String fileName = "user-" + user.getString("name").toLowerCase().replaceAll("\\s+", "-") + ".pdf";
              String filePath = "uploads/" + fileName;
              PdfWriter.getInstance(document, new FileOutputStream(filePath));

              document.open();


              Font titleFont = new Font(Font.FontFamily.HELVETICA, 20);
              Paragraph title = new Paragraph("User Details", titleFont);
              title.setAlignment(Element.ALIGN_CENTER);
              document.add(title);
              document.add(new Paragraph("\n"));


              Font normalFont = new Font(Font.FontFamily.HELVETICA, 12);
              Paragraph date = new Paragraph("Generated on: " +
                  LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), normalFont);
              document.add(date);
              document.add(new Paragraph("\n"));


              PdfPTable table = new PdfPTable(2);
              table.setWidthPercentage(100);
              table.setSpacingBefore(10f);
              table.setSpacingAfter(10f);


              BaseColor headerColor = new BaseColor(41, 128, 185);
              Font headerFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, BaseColor.WHITE);

              PdfPCell headerCell1 = new PdfPCell(new Phrase("Field", headerFont));
              PdfPCell headerCell2 = new PdfPCell(new Phrase("Value", headerFont));

              headerCell1.setBackgroundColor(headerColor);
              headerCell2.setBackgroundColor(headerColor);
              headerCell1.setPadding(8);
              headerCell2.setPadding(8);

              table.addCell(headerCell1);
              table.addCell(headerCell2);


              Font dataFont = new Font(Font.FontFamily.HELVETICA, 12);
              addTableRow(table, "User ID", user.getString("_id"), dataFont);
              addTableRow(table, "Name", user.getString("name"), dataFont);
              addTableRow(table, "Email", user.getString("email"), dataFont);

              document.add(table);


              Font footerFont = new Font(Font.FontFamily.HELVETICA, 10);
              Paragraph footer = new Paragraph("Task Management System - Generated Report", footerFont);
              footer.setAlignment(Element.ALIGN_CENTER);
              document.add(footer);

              document.close();

              ctx.response()
                  .setStatusCode(200)
                  .putHeader("Content-Type", "application/json")
                  .end(new JsonObject().put("filePath", fileName).encode());

          } catch (Exception e) {
              ctx.response()
                  .setStatusCode(500)
                  .putHeader("Content-Type", "application/json")
                  .end(new JsonObject().put("error", "Failed to generate PDF").encode());
          }
      } else {
          ctx.response()
              .setStatusCode(404)
              .putHeader("Content-Type", "application/json")
              .end(new JsonObject().put("error", "User not found").encode());
      }
  });
}

private void addTableRow(PdfPTable table, String field, String value, Font font) {
  PdfPCell cell1 = new PdfPCell(new Phrase(field, font));
  PdfPCell cell2 = new PdfPCell(new Phrase(value, font));
  cell1.setPadding(8);
  cell2.setPadding(8);
  table.addCell(cell1);
  table.addCell(cell2);
}

// --------------Promise excel----------------------

private Promise<JsonArray> findAllUsers() {
  Promise<JsonArray> promise = Promise.promise();
  JsonObject query = new JsonObject()
      .put("collection", "users")
      .put("query", new JsonObject());

  vertx.eventBus().request("db.find", query, ar -> {
      if (ar.succeeded()) {
          promise.complete((JsonArray) ar.result().body());
      } else {
          promise.fail("Failed to fetch users data");
      }
  });
  return promise;
}

private Promise<String> generateExcelFile(JsonArray users) {
  Promise<String> promise = Promise.promise();
  try {
      XSSFWorkbook workbook = new XSSFWorkbook();
      XSSFSheet sheet = workbook.createSheet("Users List");


      XSSFCellStyle headerStyle = workbook.createCellStyle();
      headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      headerStyle.setBorderBottom(BorderStyle.THIN);
      headerStyle.setBorderTop(BorderStyle.THIN);
      headerStyle.setBorderRight(BorderStyle.THIN);
      headerStyle.setBorderLeft(BorderStyle.THIN);

      XSSFFont headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerStyle.setFont(headerFont);


      Row headerRow = sheet.createRow(0);
      String[] headers = {"ID", "Name", "Email", "Created Date"};

      for (int i = 0; i < headers.length; i++) {
          Cell cell = headerRow.createCell(i);
          cell.setCellValue(headers[i]);
          cell.setCellStyle(headerStyle);
          sheet.autoSizeColumn(i);
      }


      XSSFCellStyle dataStyle = workbook.createCellStyle();
      dataStyle.setBorderBottom(BorderStyle.THIN);
      dataStyle.setBorderTop(BorderStyle.THIN);
      dataStyle.setBorderRight(BorderStyle.THIN);
      dataStyle.setBorderLeft(BorderStyle.THIN);


      int rowNum = 1;
      for (int i = 0; i < users.size(); i++) {
          JsonObject user = users.getJsonObject(i);
          Row row = sheet.createRow(rowNum++);

          Cell idCell = row.createCell(0);
          idCell.setCellValue(user.getString("_id"));
          idCell.setCellStyle(dataStyle);

          Cell nameCell = row.createCell(1);
          nameCell.setCellValue(user.getString("name"));
          nameCell.setCellStyle(dataStyle);

          Cell emailCell = row.createCell(2);
          emailCell.setCellValue(user.getString("email"));
          emailCell.setCellStyle(dataStyle);

          Cell dateCell = row.createCell(3);
          dateCell.setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
          dateCell.setCellStyle(dataStyle);

          for (int j = 0; j < headers.length; j++) {
              sheet.autoSizeColumn(j);
          }
      }

      String fileName = "users-" + UUID.randomUUID().toString() + ".xlsx";
      String filePath = "uploads/" + fileName;

      FileOutputStream fileOut = new FileOutputStream(filePath);
      workbook.write(fileOut);
      fileOut.close();
      workbook.close();

      promise.complete(fileName);
  } catch (Exception e) {
      promise.fail(e);
  }
  return promise;
}

// ------------------------Handler excel-----------------------
private void generateUsersExcel(RoutingContext ctx) {
  findAllUsers()
      .future().compose(users -> generateExcelFile(users).future())
      .map(fileName -> new JsonObject()
          .put("filePath", fileName)
          .put("message", "Excel file generated successfully"))
      .onSuccess(result -> {
          ctx.response()
              .setStatusCode(200)
              .putHeader("Content-Type", "application/json")
              .end(result.encode());
      })
      .onFailure(err -> {
          ctx.response()
              .setStatusCode(500)
              .putHeader("Content-Type", "application/json")
              .end(new JsonObject()
                  .put("error", "Failed to generate Excel file: " + err.getMessage())
                  .encode());
      });
}



}

