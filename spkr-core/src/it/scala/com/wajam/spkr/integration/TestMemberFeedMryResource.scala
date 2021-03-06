package com.wajam.spkr.integration

import org.scalatest.BeforeAndAfterEach
import org.scalatest.{Matchers => ShouldMatchers}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.wajam.spkr.mry.model.Member
import com.wajam.spkr.resources.JsonHelper

@RunWith(classOf[JUnitRunner])
class TestMemberFeedMryResource
  extends SpkrIntegrationTest with SpkrAPITest with BeforeAndAfterEach with ShouldMatchers with JsonHelper {

  test("It should be possible to get a user's feed") {
    val newMember = generateMember
    val newMembersUsername: String = newMember(Member.username).toString
    insertMember(newMember)

    getFeed(newMembersUsername)._1 should equal(200)
  }


  test("A new message should appear in the author's feed (through percolation)") {
    val newMember = generateMember
    val newMembersUsername: String = newMember(Member.username).toString
    val newMembersDisplayName: String = newMember(Member.displayName).toString
    insertMember(newMember)

    extractList(getFeed(newMembersUsername)._2).size should equal(0)

    // Wait for the self-subscription to succeed
    Thread.sleep(500)

    // Post a new message
    insertMessage(newMembersUsername,this.generateMessage(newMembersUsername,newMembersDisplayName))

    waitForCondition[Int](() => { extractList(getFeed(newMembersUsername)._2).size }, _ == 1)
  }

  test("A new message should appear in the subscribers' feeds (through percolation)") {
    val author = generateMember
    val subscriber = generateMember
    val authorUsername: String = author(Member.username).toString
    val authorDisplayName: String = author(Member.displayName).toString
    val subscriberUsername: String = subscriber(Member.username).toString
    insertMember(author)
    insertMember(subscriber)

    insertSubscription(subscriberUsername, generateSubscription(subscriber,author))

    // Let the percolation turn the subscription into a subscriber
    Thread.sleep(500)

    insertMessage(authorUsername,this.generateMessage(authorUsername,authorDisplayName))

    waitForCondition[Int](() => { extractList(getFeed(subscriberUsername)._2).size }, _ == 1)
  }

}
