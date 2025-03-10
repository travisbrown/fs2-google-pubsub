package com.permutive.pubsub.producer.http

import cats.effect.{Concurrent, Resource, Timer}
import com.permutive.pubsub.producer.encoder.MessageEncoder
import com.permutive.pubsub.producer.http.internal.{BatchingHttpPublisher, DefaultHttpPublisher}
import com.permutive.pubsub.producer.{AsyncPubsubProducer, Model}
import io.chrisdavenport.log4cats.Logger
import org.http4s.client.Client

object BatchingHttpPubsubProducer {
  def resource[F[_] : Concurrent : Timer : Logger, A: MessageEncoder](
    projectId: Model.ProjectId,
    topic: Model.Topic,
    googleServiceAccountPath: String,
    config: PubsubHttpProducerConfig[F],
    batchingConfig: BatchingHttpProducerConfig,
    httpClient: Client[F],
  ): Resource[F, AsyncPubsubProducer[F, A]] = {
    for {
      publisher <- DefaultHttpPublisher.resource(
        projectId = projectId,
        topic = topic,
        serviceAccountPath = googleServiceAccountPath,
        config = config,
        httpClient = httpClient
      )
      batching <- BatchingHttpPublisher.resource(
        publisher = publisher,
        config = batchingConfig,
      )
    } yield batching
  }
}
