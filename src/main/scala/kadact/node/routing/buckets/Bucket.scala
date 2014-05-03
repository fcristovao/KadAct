package kadact.node.routing.buckets

import scala.collection.immutable.TreeSet

import kadact.node.Contact
import kadact.node.routing.TimestampedContact

abstract class Bucket(val maxSize: Int) {
  def queue: TreeSet[TimestampedContact]

  def queue_=(set: TreeSet[TimestampedContact]): Unit

  def isFull: Boolean = {
    queue.size >= maxSize
  }

  /** Tries to insert the contact in the bucket.
    * If the contact belongs in this bucket, it returns true.
    * If, by inserting the contact, another contact has to be removed,
    * it returns that contact as well.
    *
    * @param contact
    * @return Whether the contact was inserted or not and, if it was, the contact that was removed to make space for it (it might not happen though)
    */
  def insertOrUpdate(contact: Contact): (Boolean, Option[TimestampedContact]) = {
    insertOrUpdate(new TimestampedContact(System.currentTimeMillis(), contact))
  }

  def insertOrUpdate(tsContact: TimestampedContact): (Boolean, Option[TimestampedContact])

  def filter(f: (Contact) => Boolean): Set[Contact] = {
    queue.filter(tc => f(tc.contact)).map(_.contact)
  }

  def pickNNodes(n: Int): Set[Contact] = {
    queue.take(n).map(_.contact).toSet[Contact]
  }
}