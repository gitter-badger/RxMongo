/*
 * Copyright © 2015 Reactific Software LLC. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package rxmongo.bson

import java.nio.ByteOrder

import akka.util.{ ByteStringBuilder, ByteString }
import org.specs2.mutable.Specification

/**
 * Title Of Thing.
 *
 * Description of thing
 */
class BuilderSpec extends Specification {

  implicit val byteOrder = ByteOrder.LITTLE_ENDIAN

  def cstring(bldr: ByteStringBuilder, str: String): ByteStringBuilder = {
    bldr.putBytes(str.getBytes(utf8)) // c string
    bldr.putByte(0) // termination of c string
    bldr
  }

  def string(bldr: ByteStringBuilder, str: String): ByteStringBuilder = {
    val bytes = str.getBytes(utf8)
    bldr.putInt(bytes.length + 1)
    bldr.putBytes(bytes)
    bldr.putByte(0)
  }

  def field(bldr: ByteStringBuilder, code: Byte, fieldName: String): ByteStringBuilder = {
    bldr.putByte(code) // code
    cstring(bldr, fieldName)
  }

  def preamble(len: Int, code: Byte, fieldName: String): ByteStringBuilder = {
    val bldr: ByteStringBuilder = ByteString.newBuilder
    bldr.putInt(len)
    field(bldr, code, fieldName)
  }

  "Builder" should {
    "build double correctly" in {
      val data = 42.0D
      val expected: ByteString = {
        val builder = preamble(17, 1, "double")
        builder.putDouble(data) // double value
        builder.putByte(0) // terminating null
        builder.result()
      }
      val builder = Builder()
      builder.double("double", data)
      builder.result must beEqualTo(expected)
    }

    "build string correctly" in {
      val data = "fourty-two"
      val expected: ByteString = {
        val builder = preamble(24, 2, "string")
        val str = data.getBytes(utf8)
        builder.putInt(str.length + 1) // length of string
        builder.putBytes(str) // data string
        builder.putByte(0) // string terminator
        builder.putByte(0) // terminating null
        builder.result()
      }
      val builder = Builder()
      builder.string("string", data)
      builder.result must beEqualTo(expected)
    }

    "build object correctly" in {
      val expected: ByteString = {
        val builder = preamble(50, 3, "obj")
        builder.putInt(40)
        field(builder, 2, "string")
        string(builder, "fourty-two")
        field(builder, 1, "double")
        builder.putDouble(42.0D)
        builder.putByte(0) // terminating null of embedded obj
        builder.putByte(0) // terminating null of whole obj
        builder.result()
      }
      val obj = BSONObject(Map("string" -> BSONString("fourty-two"), "double" -> BSONDouble(42.0)))
      val builder2 = Builder()
      builder2.obj("obj", obj)
      builder2.result must beEqualTo(expected)
    }

    "build array correctly" in {
      val data1 = "fourty-two"
      val data2 = 42.0D
      val expected: ByteString = {
        val builder = preamble(42, 4, "array")
        builder.putInt(30)
        builder.putByte(2) // string code
        builder.putBytes("0".getBytes(utf8)) // c string
        builder.putByte(0) // termination of c string
        val str = data1.getBytes(utf8)
        builder.putInt(str.length + 1) // length of string
        builder.putBytes(str) // data string
        builder.putByte(0) // string terminator
        builder.putByte(1) // code
        builder.putBytes("1".getBytes(utf8)) // c string
        builder.putByte(0) // termination of c string
        builder.putDouble(data2) // double value
        builder.putByte(0) // terminating null
        builder.putByte(0) // terminating null
        builder.result()
      }
      val str = BSONString("fourty-two")
      val dbl = BSONDouble(42.0)
      val builder = Builder()
      builder.array("array", Array(str, dbl))
      builder.result must beEqualTo(expected)
    }

    "build binary correctly" in {
      val data = "fourty-two"
      val expected: ByteString = {
        val builder = preamble(24, 5, "binary")
        val str = data.getBytes(utf8)
        builder.putInt(str.length) // length of string
        builder.putByte(0x80.toByte) // user defined code
        builder.putBytes(str) // data string
        builder.putByte(0) // terminating null
        builder.result()
      }
      val builder = Builder()
      builder.binary("binary", data.getBytes(utf8), BinarySubtype.UserDefinedBinary)
      builder.result must beEqualTo(expected)

    }

    "build undefined correctly" in {
      val expected: ByteString = {
        val builder = preamble(12, 6, "undefined")
        builder.putByte(0) // terminating null
        builder.result()
      }
      val builder = Builder()
      builder.undefined("undefined")
      builder.result must beEqualTo(expected)
    }

    "build objectID correctly" in {
      val data = Array[Byte](0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
      val expected: ByteString = {
        val builder = preamble(23, 7, "objectid")
        builder.putBytes(data)
        builder.putByte(0)
        builder.result()
      }
      val builder = Builder()
      builder.objectID("objectid", data)
      builder.result must beEqualTo(expected)
    }

    "build boolean correctly" in {
      val expected: ByteString = {
        val builder = preamble(16, 8, "true")
        builder.putByte(1)
        field(builder, 8, "false")
        builder.putByte(0)
        builder.putByte(0)
        builder.result()
      }
      val builder = Builder()
      builder.boolean("true", value = true)
      builder.boolean("false", value = false)
      builder.result must beEqualTo(expected)
    }

    "build utcDate correctly" in {
      val data = System.currentTimeMillis()
      val expected: ByteString = {
        val builder = preamble(14, 9, "utc")
        builder.putLong(data)
        builder.putByte(0)
        builder.result()
      }
      val builder = Builder()
      builder.utcDate("utc", data)
      builder.result must beEqualTo(expected)
    }

    "build nil correctly" in {
      val expected: ByteString = {
        val builder = preamble(6, 10, "nil")
        builder.putByte(0)
        builder.result()
      }
      val builder = Builder()
      builder.nil("nil")
      builder.result must beEqualTo(expected)
    }

    "build regex correctly" in {
      val expected: ByteString = {
        val builder = preamble(23, 11, "regex")
        cstring(builder, "pattern")
        cstring(builder, "ilmsux")
        builder.putByte(0)
        builder.result()
      }
      val builder = Builder()
      builder.regex("regex", "pattern", "ilmsux")
      builder.result must beEqualTo(expected)
    }

    "build dbPointer correctly" in {
      val data = Array[Byte](0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
      val expected: ByteString = {
        val builder = preamble(37, 12, "dbpointer")
        string(builder, "referent")
        builder.putBytes(data)
        builder.putByte(0)
        builder.result()
      }
      val builder = Builder()
      builder.dbPointer("dbpointer", "referent", data)
      builder.result must beEqualTo(expected)
    }

    "build jsCode correctly" in {
      val code = "function(x) { return 0; };"
      val expected: ByteString = {
        val builder = preamble(code.length + 5 + 8 + 1, 13, "jscode")
        string(builder, code)
        builder.putByte(0)
        builder.result()
      }
      val builder = Builder()
      builder.jsCode("jscode", code)
      builder.result must beEqualTo(expected)
    }

    "build symbol correctly" in {
      val symbol = "symbol"
      val expected: ByteString = {
        val builder = preamble(symbol.length + 5 + 8 + 1, 14, "symbol")
        string(builder, symbol)
        builder.putByte(0)
        builder.result()
      }
      val builder = Builder()
      builder.symbol("symbol", symbol)
      builder.result must beEqualTo(expected)
    }

    "build scopedJsCode correctly" in {
      val code = "function(x) { return 0; };"
      val expected: ByteString = {
        val builder = preamble(48 + code.length + 5 + 14 + 1, 15, "scopedJsCode")
        builder.putInt(44 + code.length + 5)
        string(builder, code)
        builder.putInt(40)
        builder.putByte(2) // string code
        builder.putBytes("string".getBytes(utf8)) // c string
        builder.putByte(0) // termination of c string
        val str = "fourty-two".getBytes(utf8)
        builder.putInt(str.length + 1) // length of string
        builder.putBytes(str) // data string
        builder.putByte(0) // string terminator
        builder.putByte(1) // code
        builder.putBytes("double".getBytes(utf8)) // c string
        builder.putByte(0) // termination of c string
        builder.putDouble(42.0) // double value
        builder.putByte(0) // terminating null
        builder.putByte(0) // terminating null
        builder.result()
      }
      val obj = BSONObject(Map("string" -> BSONString("fourty-two"), "double" -> BSONDouble(42.0)))
      val builder2 = Builder()
      builder2.scopedJsCode("scopedJsCode", code, obj)
      builder2.result must beEqualTo(expected)

    }

    "build integer correctly" in {
      val data = 42
      val expected: ByteString = {
        val builder = preamble(5 + 9, 16, "integer")
        builder.putInt(data)
        builder.putByte(0)
        builder.result()
      }
      val builder = Builder()
      builder.integer("integer", data)
      builder.result must beEqualTo(expected)
    }

    "build timestamp correctly" in {
      val data = System.currentTimeMillis()
      val expected: ByteString = {
        val builder = preamble(9 + 11, 17, "timestamp")
        builder.putLong(data)
        builder.putByte(0)
        builder.result()
      }
      val builder = Builder()
      builder.timestamp("timestamp", data)
      builder.result must beEqualTo(expected)
    }

    "build long correctly" in {
      val data = 42L
      val expected: ByteString = {
        val builder = preamble(9 + 6, 18, "long")
        builder.putLong(data)
        builder.putByte(0)
        builder.result()
      }
      val builder = Builder()
      builder.long("long", data)
      builder.result must beEqualTo(expected)
    }

    "throw for invalid cstring" in {
      val builder = Builder()
      builder.string("name\u0000withNull", "string") should throwA[IllegalArgumentException]
    }

    "throw for invalid regex options" in {
      val builder = Builder()
      builder.regex("regex", "pattern", "fubar") should throwA[IllegalArgumentException]
    }
  }

}