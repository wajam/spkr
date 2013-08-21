package com.wajam.spkr.integration

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.wajam.spkr.mry.model.{Member, Subscription}
import com.wajam.spkr.resources.JsonHelper

@RunWith(classOf[JUnitRunner])
class TestMemberSubscriptionMryResource
  extends SpkrIntegrationTest with SpkrAPITest with BeforeAndAfterEach with ShouldMatchers with JsonHelper {

  test("a new member should be subscribed to himself") {
    val newMember = generateMember
    val newMembersUsername: String = newMember.get(Member.username).get.toString
    insertMember(newMember)

    val (getCode, subscriptionData) = getSubscription(newMembersUsername)
    getCode should equal(200)

    val newMembersSubs = extractList(subscriptionData)

    newMembersSubs.size should be (1) // There should only be a single subscription for a new user
    newMembersSubs.find(sub => sub.get(Subscription.username).get.toString == newMembersUsername
      && sub.get(Subscription.subscriptionUsername).get.toString == newMembersUsername)
      .isDefined should be(true)
  }

  test("creating a subscription should insert a subscription in the store") {

    val newMember1 = generateMember
    val newMember2 = generateMember
    val newMembersUsername1: String = newMember1.get(Member.username).get.toString
    val newMembersUsername2: String = newMember2.get(Member.username).get.toString
    insertMember(newMember1)
    insertMember(newMember2)

    // Member1 subscribes to member2
    val (postCode, _) = insertSubscription(newMembersUsername1, generateSubscription(newMember1,newMember2))
    postCode should equal(200)

    // Obtain the inserted subscription
    val (getCode, subscriptionData) = getSubscription(newMembersUsername1)
    getCode should equal(200)

    // Extract content
    val member1SubscriptionsList = extractList(subscriptionData)

    // find subscription
    member1SubscriptionsList.find(sub => sub.get(Member.username).get.toString == newMembersUsername1
      && sub.get(Subscription.subscriptionUsername).get.toString == newMembersUsername2).isDefined should be(true)
  }

  test("deleting a subscription should remove it from the store") {
    val newMember1 = generateMember
    val newMember2 = generateMember
    val newMembersUsername1: String = newMember1.get(Member.username).get.toString
    val newMembersUsername2: String = newMember2.get(Member.username).get.toString
    insertMember(newMember1)
    insertMember(newMember2)

    // Member1 subscribes to member2
    insertSubscription(newMembersUsername1, generateSubscription(newMember1,newMember2))

    // Obtain all subscriptions and count them BEFORE the delete
    val subCountBefore = extractList(getSubscription(newMembersUsername1)._2).size

    val (deleteCode, _) = deleteSubscription(newMembersUsername1,newMembersUsername2)
    deleteCode should equal(200)

    // Count subs AFTER the delete
    val subCountAfter = extractList(getSubscription(newMembersUsername1)._2).size

    subCountAfter should equal(subCountBefore - 1)

  }
}
