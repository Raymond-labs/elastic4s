package com.sksamuel.elastic4s.testkit

import java.util

import com.sksamuel.elastic4s.bulk.RichBulkResponse
import com.sksamuel.elastic4s.http.Shards
import com.sksamuel.elastic4s.http.bulk.{BulkResponse, BulkResponseItem, Index}
import com.sksamuel.elastic4s.http.delete.{DeleteByQueryResponse, DeleteResponse}
import com.sksamuel.elastic4s.http.index._
import com.sksamuel.elastic4s.http.search.{SearchHit, SearchHits}
import com.sksamuel.elastic4s.index.RichIndexResponse
import com.sksamuel.elastic4s.searches.RichSearchResponse
import org.elasticsearch.action.DocWriteResponse
import org.elasticsearch.action.admin.indices.cache.clear.ClearIndicesCacheResponse
import org.elasticsearch.action.admin.indices.create.{CreateIndexResponse => TcpCreateIndexResponse}
import org.elasticsearch.action.admin.indices.delete.{DeleteIndexResponse => TcpDeleteIndexResponse}
import org.elasticsearch.action.admin.indices.open.{OpenIndexResponse => TcpOpenIndexResponse}
import org.elasticsearch.action.admin.indices.close.{CloseIndexResponse => TcpCloseIndexResponse}
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse
import org.elasticsearch.action.admin.indices.exists.types.TypesExistsResponse
import org.elasticsearch.action.admin.indices.flush.FlushResponse
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse
import org.elasticsearch.action.delete.{DeleteResponse => TcpDeleteResponse}
import org.elasticsearch.index.reindex.{BulkByScrollTask, BulkIndexByScrollResponse}

import scala.collection.JavaConverters._

trait ResponseConverter[T, R] {
  def convert(response: T): R
}

object ResponseConverterImplicits {

  import com.sksamuel.elastic4s.http.search.SearchResponse

  implicit object FlushIndexResponseConverter extends ResponseConverter[FlushResponse, FlushIndexResponse] {
    override def convert(response: FlushResponse) = FlushIndexResponse(
      Shards(response.getTotalShards, response.getFailedShards, response.getSuccessfulShards)
    )
  }

  implicit object IndexResponseConverter extends ResponseConverter[RichIndexResponse, IndexResponse] {
    override def convert(response: RichIndexResponse) = {
      val shardInfo = response.original.getShardInfo

      IndexResponse(
        response.id,
        response.index,
        response.`type`,
        response.version,
        response.original.getResult.getLowercase,
        response.original.forcedRefresh(),
        Shards(shardInfo.getTotal, shardInfo.getFailed, shardInfo.getSuccessful),
        response.created
      )
    }
  }

  implicit object CreateIndexResponseConverter extends ResponseConverter[TcpCreateIndexResponse, CreateIndexResponse] {
    override def convert(response: TcpCreateIndexResponse) = CreateIndexResponse(
      response.isAcknowledged, response.isShardsAcked
    )
  }

  implicit object BulkResponseConverter extends ResponseConverter[RichBulkResponse, BulkResponse] {
    override def convert(response: RichBulkResponse) = BulkResponse(
      response.took.toMillis,
      response.hasFailures,
      response.items.map { x =>
        BulkResponseItem(
          Index(
            x.index,
            x.`type`,
            x.id,
            x.version,
            x.original.status().getStatus.toString, // TODO: The model might need to be changed to use Int instead
            null // TODO: Will likely need to be obtained from original.getResponse
          ))
      }
    )
  }

  implicit object SearchResponseConverter extends ResponseConverter[RichSearchResponse, SearchResponse] {
    override def convert(response: RichSearchResponse) = SearchResponse(
      response.tookInMillis.toInt,
      response.isTimedOut,
      response.isTerminatedEarly,
      null, // TODO
      Shards(response.totalShards, response.shardFailures.length, response.successfulShards),
      response.scrollId,
      null, // TODO: Aggregations are still being working on
      SearchHits(
        response.totalHits.toInt,
        response.maxScore,
        response.hits.map(x => SearchHit(
          x.id,
          x.index,
          x.`type`,
          x.score,
          x.sourceAsMap.asScalaNested,
          x.fields,
          Map.empty, // TODO
          x.version
        ))
      ))
  }

  implicit object IndexExistsResponseConverter extends ResponseConverter[IndicesExistsResponse, IndexExistsResponse] {
    override def convert(response: IndicesExistsResponse) = IndexExistsResponse(response.isExists)
  }

  implicit object DeleteIndexResponseConverter extends ResponseConverter[TcpDeleteIndexResponse, DeleteIndexResponse] {
    override def convert(response: TcpDeleteIndexResponse) = DeleteIndexResponse(response.isAcknowledged)
  }

  implicit object OpenIndexResponseConverter extends ResponseConverter[TcpOpenIndexResponse, OpenIndexResponse] {
    override def convert(response: TcpOpenIndexResponse) = OpenIndexResponse(response.isAcknowledged)
  }

  implicit object CloseIndexResponseConverter extends ResponseConverter[TcpCloseIndexResponse, CloseIndexResponse] {
    override def convert(response: TcpCloseIndexResponse) = CloseIndexResponse(response.isAcknowledged)
  }

  implicit object RefreshIndexResponseConverter extends ResponseConverter[RefreshResponse, RefreshIndexResponse] {
    override def convert(response: RefreshResponse) = RefreshIndexResponse()
  }

  implicit object TypeExistsResponseConverter extends ResponseConverter[TypesExistsResponse, TypeExistsResponse] {
    override def convert(response: TypesExistsResponse) = TypeExistsResponse(response.isExists)
  }

  implicit object ClearCacheResponseConverter extends ResponseConverter[ClearIndicesCacheResponse, ClearCacheResponse] {
    override def convert(response: ClearIndicesCacheResponse) = ClearCacheResponse(
      Shards(
        response.getTotalShards,
        response.getFailedShards,
        response.getSuccessfulShards
      )
    )
  }

  implicit object DeleteResponseConverter extends ResponseConverter[TcpDeleteResponse, DeleteResponse] {
    override def convert(response: TcpDeleteResponse) = {
      val shardInfo = response.getShardInfo

      DeleteResponse(
        Shards(shardInfo.getTotal, shardInfo.getFailed, shardInfo.getSuccessful),
        response.getResult == DocWriteResponse.Result.DELETED,
        response.getIndex,
        response.getType,
        response.getId,
        response.getVersion,
        response.getResult.getLowercase
      )
    }
  }

  implicit object DeleteByQueryResponseConverter extends ResponseConverter[BulkIndexByScrollResponse, DeleteByQueryResponse] {
    override def convert(response: BulkIndexByScrollResponse) = {
      val field = classOf[BulkIndexByScrollResponse].getDeclaredField("status")
      field.setAccessible(true)
      val status = field.get(response).asInstanceOf[BulkByScrollTask.Status]

      DeleteByQueryResponse(
        response.getTook.millis,
        response.isTimedOut,
        status.getTotal,
        response.getDeleted,
        response.getBatches,
        response.getVersionConflicts,
        response.getNoops,
        status.getThrottled.millis,
        if(status.getRequestsPerSecond == Float.PositiveInfinity) -1 else status.getRequestsPerSecond.toLong,
        status.getThrottledUntil.millis
      )
    }
  }

  implicit class RichSourceMap(val self: Map[String, AnyRef]) extends AnyVal {
    def asScalaNested: Map[String, AnyRef] = {
      def toScala(a: AnyRef): AnyRef = a match {
        case i: java.lang.Iterable[AnyRef] => i.asScala.map(toScala)
        case m: util.Map[AnyRef, AnyRef] => m.asScala.map { case (k, v) => (toScala(k), toScala(v)) }
        case other => other
      }

      self.mapValues(toScala)
    }
  }

}
