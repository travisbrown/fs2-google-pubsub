package com.permutive.pubsub.producer.grpc.internal

import java.util.UUID
import java.util.concurrent.Executor

import cats.Traverse
import cats.effect.Async
import cats.syntax.all._
import com.google.api.core.{ApiFutureCallback, ApiFutures}
import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.permutive.pubsub.producer.Model.MessageId
import com.permutive.pubsub.producer.{Model, PubsubProducer}
import com.permutive.pubsub.producer.encoder.MessageEncoder

import scala.collection.JavaConverters._

private[pubsub] class DefaultPublisher[F[_], A: MessageEncoder](
  publisher: Publisher,
  callbackExecutor: Executor,
)(
  implicit F: Async[F]
) extends PubsubProducer[F, A] {
  final override def produce(
    record: A,
    metadata: Map[String, String] = Map.empty,
    uniqueId: String = UUID.randomUUID.toString,
  ): F[MessageId] = {
    MessageEncoder[A].encode(record) match {
      case Left(e) =>
        F.raiseError(e)
      case Right(v) =>
        val message =
          PubsubMessage
            .newBuilder
            .setData(ByteString.copyFrom(v))
            .setMessageId(uniqueId)
            .putAllAttributes(metadata.asJava)
            .build()

        for {
          future <- F.delay(publisher.publish(message))
          result <- F.async[MessageId] { cb =>
            ApiFutures.addCallback(future, new ApiFutureCallback[String] {
              override def onFailure(t: Throwable): Unit = cb(Left(t))
              override def onSuccess(result: String): Unit = cb(Right(MessageId(result)))
            }, callbackExecutor)
          }
        } yield result

    }
  }
  
  override def produceMany[G[_]: Traverse](records: G[Model.Record[A]]): F[List[MessageId]] =
    records.traverse(r => produce(r.value, r.metadata, r.uniqueId)).map(_.toList)
}
