const http = require("http");
const fs = require("fs");
const path = require("path");

const root = __dirname;
const types = {
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
};

http
  .createServer((request, response) => {
    const urlPath = decodeURIComponent(request.url.split("?")[0]);
    const requested =
      urlPath === "/" ? "index.html" : urlPath.replace(/^\/+/, "");
    const filePath = path.resolve(root, requested);

    if (
      !filePath.startsWith(root + path.sep) &&
      filePath !== path.join(root, "index.html")
    ) {
      response.writeHead(403);
      response.end("Forbidden");
      return;
    }

    fs.readFile(filePath, (error, content) => {
      if (error) {
        response.writeHead(error.code === "ENOENT" ? 404 : 500, {
          "Content-Type": "text/plain; charset=utf-8",
        });
        response.end(
          error.code === "ENOENT" ? "Not found" : "Unable to read file",
        );
        return;
      }
      response.writeHead(200, {
        "Content-Type":
          types[path.extname(filePath)] || "application/octet-stream",
      });
      response.end(content);
    });
  })
  .listen(5500, "127.0.0.1", () => {
    console.log("CivicConnect frontend running at http://localhost:5500");
  });
