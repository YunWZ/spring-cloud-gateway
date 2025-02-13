/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.tests.grpc;

import java.io.File;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerCredentials;
import io.grpc.TlsServerCredentials;
import io.grpc.stub.StreamObserver;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.config.GRPCSSLContext;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.test.TestSocketUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * @author Alberto C. Ríos
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class GRPCApplication {

	private static final int GRPC_SERVER_PORT = TestSocketUtils.findAvailableTcpPort();

	public static void main(String[] args) {
		SpringApplication.run(GRPCApplication.class, args);
	}

	@Bean
	public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
		return builder.routes().route("json-grpc", r -> r.path("/json/hello").filters(f -> {
			String protoDescriptor = "file:src/main/proto/hello.pb";
			String protoFile = "file:src/main/proto/hello.proto";
			String service = "HelloService";
			String method = "hello";
			return f.jsonToGRPC(protoDescriptor, protoFile, service, method);
		}).uri("https://localhost:" + GRPC_SERVER_PORT))
				.route("grpc", r -> r.predicate(p -> true).uri("https://localhost:" + GRPC_SERVER_PORT)).build();
	}

	@Bean
	public GRPCSSLContext sslContext() throws SSLException {
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		} };

		return new GRPCSSLContext(trustAllCerts[0]);
	}

	@Component
	static class GRPCServer implements ApplicationRunner {

		private Server server;

		@Override
		public void run(ApplicationArguments args) throws Exception {
			final GRPCServer server = new GRPCServer();
			server.start();
		}

		private void start() throws Exception {
			/* The port on which the server should run */
			ServerCredentials creds = createServerCredentials();
			server = Grpc.newServerBuilderForPort(GRPC_SERVER_PORT, creds).addService(new HelloService()).build()
					.start();

			System.out.println("Starting gRPC server in port " + GRPC_SERVER_PORT);

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					GRPCServer.this.stop();
				}
				catch (InterruptedException e) {
					e.printStackTrace(System.err);
				}
			}));
		}

		private ServerCredentials createServerCredentials() throws IOException {
			File privateKey = new ClassPathResource("private.key").getFile();
			File certChain = new ClassPathResource("certificate.pem").getFile();
			return TlsServerCredentials.create(certChain, privateKey);
		}

		private void stop() throws InterruptedException {
			if (server != null) {
				server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
			}
		}

		static class HelloService extends HelloServiceGrpc.HelloServiceImplBase {

			@Override
			public void hello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {

				String greeting = "Hello, " + request.getFirstName() + " " + request.getLastName();
				System.out.println("Sending response: " + greeting);

				HelloResponse response = HelloResponse.newBuilder().setGreeting(greeting).build();

				responseObserver.onNext(response);
				responseObserver.onCompleted();
			}

		}

	}

}
