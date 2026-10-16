package com.ganj.vpn.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.ganj.vpn.MainActivity
import com.ganj.vpn.core.controlapi.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GanjFamilyInteractionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun render(content:@Composable ()->Unit) {compose.activity.setContent{GanjTheme(darkTheme=true){Surface{content()}}}}
    private fun capture(name:String) {compose.waitForIdle();UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(compose.activity.cacheDir,"$name.png"))}
    @Test fun invitationRequiresTelegramAndDoesNotJoinOnOpening() {
        val api=FixtureFamily();var login=0
        render {GanjFamilyInvitation(api,"x".repeat(43),false,{login++},{},{})}
        compose.onNodeWithText("ورود با تلگرام").performClick()
        compose.runOnIdle {assertEquals(1,login);assertEquals(0,api.previews);assertEquals(0,api.accepted)}
    }
    @Test fun invitationNamesInviterAndOnlyExplicitAcceptJoins() {
        val api=FixtureFamily();var joined=0
        render {GanjFamilyInvitation(api,"x".repeat(43),true,{},{},{joined++})}
        compose.waitUntil(10000){compose.onAllNodesWithText("قبول دعوت").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("دعوت‌کننده نمونه",substring=true).assertExists()
        compose.runOnIdle {assertEquals(0,api.accepted)}
        capture("family-invitation-dark")
        compose.onNodeWithText("قبول دعوت").performClick()
        compose.waitUntil(10000){joined==1}
        compose.runOnIdle {assertEquals(1,api.accepted)}
    }
    private class FixtureFamily:FamilyApi {
        @Volatile var previews=0;@Volatile var accepted=0
        private fun reply()=ApiResult.Success(FamilyReply(serviceId="00000000-0000-4000-8000-000000000002",serviceName="اکونومی",owner=false,ownInvite=false,deviceLimit=2,slotsUsed=1,inviterName="دعوت‌کننده نمونه",inviteUrl=null,active=true,members=emptyList(),devices=emptyList(),invites=emptyList()),ResponseMetadata(null,null))
        override fun preview(token:String):ApiResult<FamilyReply>{previews++;return reply()}
        override fun accept(token:String):ApiResult<FamilyReply>{accepted++;return reply()}
        override fun summary(serviceId:String)=reply()
        override fun access(serviceId:String)=reply()
        override fun createInvite(serviceId:String)=reply()
        override fun remove(serviceId:String,memberId:String)=reply()
        override fun revokeInvite(serviceId:String,inviteId:String)=reply()
        override fun updateDevice(name:String,appVersion:String,osVersion:String)=reply()
    }
}
