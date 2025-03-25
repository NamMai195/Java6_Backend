# Dịch vụ Backend-STM - README

## Giới thiệu

Tài liệu này hướng dẫn cách cài đặt, xây dựng, chạy và kiểm thử dịch vụ backend. Dịch vụ này sử dụng Java 17, Spring Boot 3.3.4 và Docker để đóng gói ứng dụng.

## 1. Yêu cầu tiên quyết

Trước khi bắt đầu, hãy đảm bảo bạn đã cài đặt và cấu hình các công cụ sau trên hệ thống của mình:

-   **JDK 17+**: Bộ công cụ phát triển Java phiên bản 17 trở lên.
    -   [Tải JDK](https://adoptium.net/)
-   **Maven 3.5+**: Công cụ tự động hóa xây dựng cho dự án Java.
    -   [Tải Maven](https://maven.apache.org/download.cgi)
-   **IntelliJ IDEA** (Tùy chọn): Môi trường phát triển tích hợp cho Java.
    -   [Tải IntelliJ IDEA](https://www.jetbrains.com/idea/download/)
-   **Docker**: Nền tảng container hóa.
    -   [Tải Docker](https://www.docker.com/get-started/)

## 2. Các công nghệ sử dụng

Dịch vụ backend sử dụng các công nghệ sau:

-   **Java 17**
-   **Maven 3.5+**
-   **Spring Boot 3.3.4**
-   **Spring Data Validation**
-   **Spring Data JPA**
-   **PostgreSQL / MySQL** (Tùy chọn)
-   **Lombok**
-   **Spring Boot DevTools**
-   **Docker**
-   **Docker Compose**

## 3. Xây dựng & Chạy ứng dụng

### 3.1. Chạy bằng Maven Wrapper (mvnw)

1.  Mở terminal và di chuyển đến thư mục `backend-service`.
2.  Chạy lệnh sau:

    ```bash
    ./mvnw spring-boot:run
    ```

    Lệnh này biên dịch và chạy ứng dụng Spring Boot sử dụng Maven Wrapper.

### 3.2. Chạy bằng Docker

1.  Mở terminal và di chuyển đến thư mục `backend-service`.
2.  Xây dựng Docker image:

    ```bash
    mvn clean install -P dev
    docker build -t backend-service:latest .
    ```

    -   `mvn clean install -P dev` xây dựng ứng dụng với profile `dev`.
    -   `docker build -t backend-service:latest .` xây dựng Docker image với tên `backend-service:latest` sử dụng Dockerfile trong thư mục hiện tại.
3.  Chạy Docker container:

    ```bash
    docker run -it -p 8080:8080 --name backend-service backend-service:latest
    ```

    -   `docker run` khởi động container mới.
    -   `-it` cấp phát pseudo-TTY và giữ STDIN mở.
    -   `-p 8080:8080` ánh xạ cổng 8080 của container với cổng 8080 trên máy chủ.
    -   `--name backend-service` đặt tên container là `backend-service`.
    -   `backend-service:latest` chỉ định image cần sử dụng.

## 4. Kiểm thử

### 4.1. Kiểm tra trạng thái ứng dụng

Sử dụng `curl` để kiểm tra trạng thái ứng dụng:

```bash
curl --location 'http://localhost:8080/actuator/health'
Kết quả mong đợi:

JSON

{
  "status": "UP"
}
4.2. Kiểm thử API
Truy cập dịch vụ backend tại http://localhost:8080 để kiểm thử các API có sẵn. Bạn có thể sử dụng các công cụ như Postman, Insomnia hoặc curl để gửi yêu cầu đến các endpoint.

5. Cấu hình tùy chọn
Cấu hình Database:
Nếu sử dụng PostgreSQL hoặc MySQL, cấu hình thông tin kết nối database trong file application.properties hoặc application.yml.
Bạn có thể sử dụng docker-compose để chạy container database.
Docker Compose:
Nếu sử dụng Docker Compose, tạo file docker-compose.yml để quản lý ứng dụng và các dependency (ví dụ: database).
Sử dụng docker-compose up để khởi động ứng dụng.
6. Phát triển
IntelliJ IDEA: Import dự án vào IntelliJ IDEA dưới dạng dự án Maven.
Lombok: Đảm bảo Lombok được cấu hình trong IDE để kích hoạt xử lý annotation.
DevTools: Spring Boot DevTools sẽ tự động tải lại ứng dụng khi có thay đổi mã nguồn trong quá trình phát triển.