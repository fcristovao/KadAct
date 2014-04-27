package kadact

import kadact.node._

package object nodeForTest {
  implicit def intToKey(int: Int): Key = BigInt(int)
}
