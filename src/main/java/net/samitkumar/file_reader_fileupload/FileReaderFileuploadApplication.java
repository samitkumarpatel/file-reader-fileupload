package net.samitkumar.file_reader_fileupload;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.Map;

@SpringBootApplication
@RequiredArgsConstructor
public class FileReaderFileuploadApplication {

	public static void main(String[] args) {
		SpringApplication.run(FileReaderFileuploadApplication.class, args);
	}

	final ReactiveRedisTemplate<String, String> redisTemplate;

	@Value("${spring.application.file.upload.path}")
	private String fileUploadPath;

	@Bean
	RouterFunction<ServerResponse> routerFunction() {
		return RouterFunctions
				.route()
				.POST("/message/to/channel", request -> {
					return request
							.bodyToMono(String.class)
							.flatMap(s -> redisTemplate.convertAndSend("channel", s))
							.map(l -> Map.of("status", "SUCCESS", "l", l))
							.flatMap(ServerResponse.ok()::bodyValue);
				})
				.POST("/file/upload", this::upload)
				.build();
	}

	private Mono<ServerResponse> upload(ServerRequest request) {
		return request
				.multipartData()
				.map(MultiValueMap::toSingleValueMap)
				.map(stringPartMap -> stringPartMap.get("file"))
				.cast(FilePart.class)
				.flatMap(this::fileUploadToDisk)
				.flatMap(filePart -> redisTemplate.convertAndSend("channel", filePart.filename()))//spring.application.file.upload.path=${FILE_LOOKUP_PATH:/tmp/upload}
				.then(ServerResponse.ok().bodyValue(Map.of("status", "SUCCESS")))
				.onErrorResume(ex -> ServerResponse
						.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.bodyValue(Map.of("status", "ERROR", "message", ex.getMessage()))
				);
	}

	private Mono<FilePart> fileUploadToDisk(FilePart filePart) {
		return filePart.transferTo(Path.of(fileUploadPath).resolve(filePart.filename()))
				.thenReturn(filePart);
	}

}
