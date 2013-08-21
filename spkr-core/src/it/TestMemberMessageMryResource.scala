package com.wajam.spkr.integration

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.wajam.spkr.mry.model.Member
import com.wajam.spkr.resources.JsonHelper

@RunWith(classOf[JUnitRunner])
class TestMemberMessageMryResource
  extends SpkrIntegrationTest with SpkrAPITest with BeforeAndAfterEach with ShouldMatchers with JsonHelper {

  test("A user posting a new messages should insert a message in the database") {
    val newMember = generateMember
    val newMembersUsername: String = newMember.get(Member.username).get.toString
    val newMembersDisplayName: String = newMember.get(Member.displayName).get.toString
    insertMember(newMember)

    // Insert generated message
    val (postCode, _) = insertMessage(newMembersUsername,this.generateMessage(newMembersUsername,newMembersDisplayName))
    postCode should equal(200)
  }
}
