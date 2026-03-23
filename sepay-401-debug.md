# Bug Report: SEpay Webhook Callback bị 401 Unauthorized

## Mô tả vấn đề

Khi tích hợp SEpay vào hệ thống, luồng thanh toán hoạt động bình thường từ phía client đến SEpay. Tuy nhiên khi SEpay gọi callback về server (webhook) để thông báo thanh toán thành công, server trả về **HTTP 401 Unauthorized**.

### Môi trường

- **Framework:** Spring Boot + Spring Security 6
- **Auth mechanism:** JWT (custom `JwtDecoder`)
- **Endpoint bị lỗi:** `POST /payments/sepay`

### Nguyên nhân gốc rễ (suspected)

SEpay gửi kèm header `Authorization: Apikey <token>` trong request callback. Spring Security nhận thấy header `Authorization` tồn tại và cố gắng decode nó như một JWT token → fail → trả về 401, **dù endpoint đã được config là `permitAll()`**.

> `permitAll()` chỉ bỏ qua yêu cầu *phải có auth*, nhưng **JWT Filter vẫn chạy** và throw 401 khi gặp token không hợp lệ.

---

## Những cách đã thử

### ❌ Cách 1: Thêm endpoint vào `PUBLIC_ENDPOINTS` với `permitAll()`

```java
private static final String[] PUBLIC_ENDPOINTS = {
    ...
    "/payments/sepay"
};

httpSecurity.authorizeHttpRequests(request ->
    request.requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()
    ...
);
```

**Kết quả:** Vẫn 401  
**Lý do thất bại:** JWT Filter vẫn chạy trước khi authorization check, gặp `Authorization: Apikey xxx` không phải JWT → throw 401 ngay.

---

### ❌ Cách 2: `WebSecurityCustomizer` với `web.ignoring()`

```java
@Bean
public WebSecurityCustomizer webSecurityCustomizer() {
    return web -> web.ignoring().requestMatchers("/payments/sepay");
}
```

**Kết quả:** Không compile / không chạy được  
**Lý do thất bại:** Từ **Spring Security 6**, `WebSecurityCustomizer.ignoring()` đã bị **deprecated và loại bỏ**.

---

### ❌ Cách 3: Custom `BearerTokenResolver` để trả về null cho SEpay route

```java
httpSecurity.oauth2ResourceServer(oauth2 -> oauth2
    .bearerTokenResolver(request -> {
        if (request.getRequestURI().startsWith("/payments/sepay")) {
            return null;
        }
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    })
    ...
);
```

**Kết quả:** Vẫn 401  
**Lý do thất bại:** Chưa xác định rõ — có thể filter chain vẫn xử lý request theo cách khác.

---

### ❌ Cách 4: Tạo `SecurityFilterChain` riêng với `@Order(1)` cho webhook

```java
@Bean
@Order(1)
public SecurityFilterChain webhookFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/payments/sepay")
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
}

@Bean
@Order(2)
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // main chain giữ nguyên
}
```

**Kết quả:** Vẫn 401  
**Lý do thất bại (suspected):** Có thể tồn tại một filter khác (`@Component` trên `OncePerRequestFilter`) đang chạy **ngoài tầm kiểm soát của Spring Security filter chain**.

---

### ❌ Cách 5: `SePayHeaderFilter` — Wrap request để xóa header `Authorization`

Tạo một `OncePerRequestFilter` với `@Order(Ordered.HIGHEST_PRECEDENCE)` để intercept request tới `/payments/sepay` và loại bỏ header `Authorization` trước khi Spring Security xử lý:

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SePayHeaderFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        if (path.contains("/payments/sepay") && authHeader != null) {
            HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request) {
                @Override
                public String getHeader(String name) {
                    if ("Authorization".equalsIgnoreCase(name)) return null;
                    return super.getHeader(name);
                }
            };
            filterChain.doFilter(wrappedRequest, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
```

**Kết quả:** Vẫn 401  
**Lý do thất bại (suspected):** Chưa xác định rõ. Có thể `JwtAuthenticationEntryPoint` hoặc `@PreAuthorize` trên controller đang chặn.

---

## Hướng điều tra tiếp theo

- [ ] Kiểm tra Payment Controller xem có annotation `@PreAuthorize` không
- [ ] Xem log của `JwtAuthenticationEntryPoint` để xác định 401 đến từ đâu
- [ ] Kiểm tra path SEpay đang gọi về có khớp với config không (trailing slash, context path...)
- [ ] Verify không có Nginx / reverse proxy nào chặn trước khi vào Spring Boot

---

## Log cần kiểm tra

```
Unauthorized error at path: /payments/sepay - <message>
```

Nếu **có log này** → 401 từ Spring Security  
Nếu **không có log này** → 401 từ `@PreAuthorize` hoặc logic trong controller
