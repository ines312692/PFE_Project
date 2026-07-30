FROM golang:1.22-alpine AS build
WORKDIR /src

COPY go.mod go.sum* ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/tls-sidecar .

FROM gcr.io/distroless/static-debian12:nonroot
WORKDIR /app

COPY --from=build /out/tls-sidecar /app/tls-sidecar

ENV TLS_CA_DIR=/app/ca
ENV TLS_SIDECAR_PORT=8888
EXPOSE 8888

USER nonroot:nonroot
ENTRYPOINT ["/app/tls-sidecar"]
