package com.wajam.spkr.integration

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.wajam.spkr.mry.model.Member
import com.wajam.spkr.resources.JsonHelper

@RunWith(classOf[JUnitRunner])
class TestMemberFeedMryResource
  extends SpkrIntegrationTest with SpkrAPITest with BeforeAndAfterEach with ShouldMatchers with JsonHelper {

  test("It should be possible to get a user's feed") {
    val newMember = generateMember
    val newMembersUsername: String = newMember.get(Member.username).get.toString
    insertMember(newMember)

    getFeed(newMembersUsername)._1 should equal(200)
  }


  test("A new message should appear in the author's feed (through percolation)") {
    val newMember = generateMember
    val newMembersUsername: String = newMember.get(Member.username).get.toString
    val newMembersDisplayName: String = newMember.get(Member.displayName).get.toString
    insertMember(newMember)

    extractList(getFeed(newMembersUsername)._2).size should equal(0)

    // Post a new message
    insertMessage(newMembersUsername,this.generateMessage(newMembersUsername,newMembersDisplayName))

    waitForCondition[Int](() => { extractList(getFeed(newMembersUsername)._2).size }, _ == 1)
  }

  test("A new message should appear in the subscribers' feeds (through percolation)") {
    val author = generateMember
    val subscriber = generateMember
    val authorUsername: String = author.get(Member.username).get.toString
    val authorDisplayName: String = author.get(Member.displayName).get.toString
    val subscriberUsername: String = subscriber.get(Member.username).get.toString
    insertMember(author)
    insertMember(subscriber)

    insertSubscription(subscriberUsername, generateSubscription(subscriber,author))

    // Let the percolation turn the subscription into a subscriber
    Thread.sleep(500)

    insertMessage(authorUsername,this.generateMessage(authorUsername,authorDisplayName))

    waitForCondition[Int](() => { extractList(getFeed(subscriberUsername)._2).size }, _ == 1)
  }



}
