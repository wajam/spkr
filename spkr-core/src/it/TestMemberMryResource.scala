package com.wajam.spkr.integration

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.wajam.mry.execution.MapValue
import com.wajam.spkr.mry.model.Member
import com.wajam.spkr.json.MryJsonConverter
import com.wajam.spkr.resources.JsonHelper

@RunWith(classOf[JUnitRunner])
class TestMemberMryResource
  extends SpkrIntegrationTest with SpkrAPITest with BeforeAndAfterEach with ShouldMatchers with JsonHelper {

  test("attempting to get a nonexistent key should return 404") {
    val (getCodeError, _) = getMember("George")
    getCodeError should equal(404)
  }

  test("creating a member should insert a member in the store") {
    val newMember = generateMember
    val (postCode, _) = insertMember(newMember)
    postCode should equal(200)

    val (getCode, data) = getMember(newMember.get(Member.username).get.toString)
    getCode should equal(200)

    val username: String = MryJsonConverter.fromJson(data) match {
      case MapValue(m) => m.get(Member.username).getOrElse("Error").toString
      case _ => "Error"
    }
    username should equal(newMember.get(Member.username).get.toString)
  }
}
