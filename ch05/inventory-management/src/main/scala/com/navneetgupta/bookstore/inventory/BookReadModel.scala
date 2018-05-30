package com.navneetgupta.bookstore.inventory

import com.navneetgupta.bookstore.common.ReadModelObject
import java.util.Date
import com.navneetgupta.bookstore.common.ViewBuilder
import akka.persistence.query.Offset
import akka.actor.Props
import com.navneetgupta.bookstore.common.ViewBuilder._
import com.navneetgupta.bookstore.common.BookstoreActor
import com.navneetgupta.bookstore.common.ElasticsearchSupport

object BookViewBuilder {
  val Name = "book-view-builder"
  case class BookRM(override val id: String, title: String,
                    author: String, tags: List[String], cost: Double,
                    inventoryAmount: Int, createTs: Date, deleted: Boolean = false)
      extends ReadModelObject

  def props = Props[BookViewBuilder]
}

trait BookReadModel {
  def indexRoot = "inventory"
  def entityType = Book.EntityType
}

class BookViewBuilder extends BookReadModel with ViewBuilder[BookViewBuilder.BookRM] {
  import Book.Event._
  import BookViewBuilder._

  def projectionId = "book-view-builder"

  override def actionFor(bookId: String, offset: Offset, event: Any): IndexAction = event match {
    case BookCreated(book) =>
      log.info("Saving a new book entity into the elasticsearch index: {}", book)
      val bookRM = BookRM(book.id, book.title, book.author, book.tags, book.cost, book.inventoryAmount,
        book.createTs, book.deleted)
      InsertAction(book.id, bookRM)

    case TagAdded(tag) =>
      log.info("Adding tag to a book entity into the elasticsearch index: {} for bookId {} ", tag, bookId)
      UpdateAction(bookId, "tags += tag", Map("tag" -> tag))

    case TagRemoved(tag) =>
      log.info("Adding tag to a book entity into the elasticsearch index: {} for bookId {} ", tag, bookId)
      UpdateAction(bookId, "tags -= tag", Map("tag" -> tag))

    case InventoryAdded(amount) =>
      log.info("InventoryAdded to a book entity into the elasticsearch index:for bookId {} with amount {} ", bookId, amount)
      UpdateAction(bookId, "inventoryAmount += amount", Map("amount" -> amount))

    case InventoryAllocated(orderId, bookId, amount) =>
      log.info("InventoryAllocated to a book entity into the elasticsearch index: for bookId {}, orderId: {}, amount: {} ", bookId, orderId, amount)
      UpdateAction(bookId, "inventoryAmount -= amount", Map("amount" -> amount))

    case BookDeleted(bookId) =>
      log.info("BookDeleted to a book entity into the elasticsearch index: bookId {} ", bookId)
      UpdateAction(bookId, "deleted = delBool", Map("delBool" -> true))

    case InventoryBackordered(orderId, bookId) =>
      NoAction(bookId)
    case evt =>
      log.info("Found UnMAtching Event evt : {}", evt)
      NoAction(bookId)
  }
}

object BookView {
  val Name = "book-view"
  case class FindBooksByTags(tags: Seq[String])
  case class FindBooksByAuthor(author: String)
  def props = Props[BookView]
}

class BookView extends BookReadModel
    with BookstoreActor with ElasticsearchSupport {
  import context.dispatcher
  import BookView._

  def receive = {
    case FindBooksByAuthor(author) =>
      log.info("=======================================================================================================================================================")
      log.info("BookView FindBooksByAuthor for author: {}", author)
      log.info("=======================================================================================================================================================")
      val results = queryElasticsearch(s"author:$author")
      pipeResponse(results)
    case FindBooksByTags(tags) =>
      log.info("=======================================================================================================================================================")

      val query = tags.map(t => s"tags:$t").mkString(" AND ")
      log.info("BookView FindBooksByTags for query: {}", query)
      log.info("=======================================================================================================================================================")

      val results = queryElasticsearch(query)
      pipeResponse(results)
  }
}
