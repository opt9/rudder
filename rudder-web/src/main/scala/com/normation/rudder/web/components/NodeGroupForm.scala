/*
*************************************************************************************
* Copyright 2011 Normation SAS
*************************************************************************************
*
* This file is part of Rudder.
*
* Rudder is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU General Public License version 3, the copyright holders add
* the following Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU General
* Public License version 3, when you create a Related Module, this
* Related Module is not considered as a part of the work and may be
* distributed under the license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* Rudder is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Rudder.  If not, see <http://www.gnu.org/licenses/>.

*
*************************************************************************************
*/

package com.normation.rudder.web.components

import com.normation.plugins.SpringExtendableSnippet
import com.normation.plugins.SnippetExtensionKey
import com.normation.rudder.AuthorizationType
import com.normation.rudder.domain.nodes._
import com.normation.rudder.domain.queries.Query
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.web.model.{
  WBTextField, FormTracker, WBTextAreaField,WBSelectField,WBRadioField
}
import com.normation.rudder.repository.FullNodeGroupCategory
import com.normation.rudder.web.components.popup.CreateCloneGroupPopup
import com.normation.rudder.web.components.popup.ModificationValidationPopup
import com.normation.rudder.web.model.CurrentUser
import com.normation.utils.HashcodeCaching
import net.liftweb.http.LocalSnippet
import net.liftweb.http.js._
import JsCmds._
import JE._
import net.liftweb.common._
import net.liftweb.http._
import scala.xml._
import net.liftweb.util.Helpers._
import bootstrap.liftweb.RudderConfig
import com.normation.rudder.domain.policies.RuleTarget
import com.normation.rudder.web.ChooseTemplate

object NodeGroupForm {
  val templatePath = "templates-hidden" :: "components" :: "NodeGroupForm" :: Nil

  val staticInit = ChooseTemplate(templatePath, "component-staticinit")
  val body = ChooseTemplate(templatePath, "component-body")
  val staticBody = ChooseTemplate(templatePath, "component-staticbody")

  private val saveButtonId = "groupSaveButtonId"

  private sealed trait RightPanel
  private case object NoPanel extends RightPanel
  private case class GroupForm(group:NodeGroup, parentCategoryId:NodeGroupCategoryId) extends RightPanel with HashcodeCaching
  private case class CategoryForm(category:NodeGroupCategory) extends RightPanel with HashcodeCaching

  val htmlId_groupTree = "groupTree"
  val htmlId_item = "ajaxItemContainer"
  val htmlId_updateContainerForm = "updateContainerForm"
}

/**
 * The form that deals with updating the server group
 */
class NodeGroupForm(
    htmlIdCategory    : String
  , val nodeGroup     : NodeGroup
  , parentCategoryId  : NodeGroupCategoryId
  , rootCategory      : FullNodeGroupCategory
  , workflowEnabled   : Boolean
  , onSuccessCallback : (Either[(NodeGroup, NodeGroupCategoryId), ChangeRequestId]) => JsCmd = { (NodeGroup) => Noop }
  , onFailureCallback : () => JsCmd = { () => Noop }
) extends DispatchSnippet with SpringExtendableSnippet[NodeGroupForm] with Loggable {
  import NodeGroupForm._

  private[this] val nodeInfoService            = RudderConfig.nodeInfoService
  private[this] val categoryHierarchyDisplayer = RudderConfig.categoryHierarchyDisplayer

  private[this] val nodeGroupCategoryForm = new LocalSnippet[NodeGroupCategoryForm]
  private[this] val nodeGroupForm = new LocalSnippet[NodeGroupForm]
  private[this] val searchNodeComponent = new LocalSnippet[SearchNodeComponent]

  private[this] var query : Option[Query] = nodeGroup.query
  private[this] var srvList : Box[Seq[NodeInfo]] = nodeInfoService.getAll.map( _.values.filter( (x:NodeInfo) => nodeGroup.serverList.contains( x.id ) ).toSeq )

  private def setSearchNodeComponent : Unit = {
    searchNodeComponent.set(Full(new SearchNodeComponent(
        htmlIdCategory
      , query
      , srvList
      , onSearchCallback = saveButtonCallBack
      , onClickCallback  = None
      , saveButtonId     = saveButtonId
      , groupPage        = false
    )))
  }

  private[this] def saveButtonCallBack(searchStatus : Boolean, query: Option[Query]) : JsCmd = {
    JsRaw(s"""$$('#${saveButtonId}').button();
        $$('#${saveButtonId}').button("option", "disabled", ${searchStatus});""")
  }

  setSearchNodeComponent

  def extendsAt = SnippetExtensionKey(classOf[NodeGroupForm].getSimpleName)

  def mainDispatch = Map(
    "showForm" -> { _:NodeSeq => showForm() },
    "showGroup" -> { _:NodeSeq => searchNodeComponent.get match {
      case Full(component) => component.buildQuery
      case _ =>  <div>The component is not set</div>
     } }
  )

  val pendingChangeRequestXml =
    <div id="pendingChangeRequestNotification">
      <div>
        <i class="fa fa-exclamation-triangle warnicon" aria-hidden="true"></i>
        <div style="float:left">
          The following pending change requests affect this Group, you should check that your modification is not already pending:
          <ul id="changeRequestList"/>
        </div>
      </div>
    </div>

  def showForm() : NodeSeq = {
     val html = SHtml.ajaxForm(body) ++
     Script(
       OnLoad(JsRaw("$('#GroupTabs').tabs( {active : 0 });" ))
     )

     (
        "group-pendingchangerequest" #>  PendingChangeRequestDisplayer.checkByGroup(pendingChangeRequestXml,nodeGroup.id, workflowEnabled)
      & "group-name" #> groupName.toForm_!
      & "group-rudderid" #> <div class="form-group row">
                      <label class="wbBaseFieldLabel">Rudder ID</label>
                      <input readonly="" class="form-control" value={nodeGroup.id.value}/>
                    </div>
      & "group-cfeclasses" #> <div class="form-group row">
                        <a href="#" onclick={s"$$('#cfe-${nodeGroup.id.value}').toggle(300);$$(this).toggleClass('open');return false;"} class="toggle-caret">
                          <label class="wbBaseFieldLabel">Display agent conditions</label>
                          <span class="caret"></span>
                        </a>
                        <div class="well row" style="display: none" id={s"cfe-${nodeGroup.id.value}"}>
                          {RuleTarget.toCFEngineClassName(nodeGroup.id.value)}<br/>
                          {RuleTarget.toCFEngineClassName(nodeGroup.name)}
                        </div>
                      </div>
      & "group-description" #> groupDescription.toForm_!
      & "group-container" #> groupContainer.toForm_!
      & "group-static" #> groupStatic.toForm_!
      & "group-showgroup" #> (searchNodeComponent.get match {
                       case Full(req) => req.buildQuery
                       case eb:EmptyBox => <span class="error">Error when retrieving the request, please try again</span>
      })
      & "group-clone" #> { if (CurrentUser.checkRights(AuthorizationType.Group.Write))
                     SHtml.ajaxButton("Clone", () => showCloneGroupPopup()) % ("id" -> "groupCloneButtonId") % ("class" -> " btn btn-default")
                   else NodeSeq.Empty
                 }
      & "group-save" #> { if (CurrentUser.checkRights(AuthorizationType.Group.Edit))
                    <div  tooltipid="saveButtonToolTip" class="tooltipable" title=""> {
                      SHtml.ajaxSubmit("Save", onSubmit _)  %  ("id" -> saveButtonId) % ("class" -> " btn btn-success")
                    } </div>
                   else NodeSeq.Empty
                }
      & "group-delete" #> SHtml.ajaxButton("Delete", () => onSubmitDelete(), ("class" -> " btn btn-danger"))
      & "group-notifications" #> updateAndDisplayNotifications()
    )(html)
   }

  ///////////// fields for category settings ///////////////////
  private[this] val groupName = {
    new WBTextField("Group name", nodeGroup.name) {
      override def setFilter = notNull _ :: trim _ :: Nil
      override def className = "form-control"
      override def labelClassName = ""
      override def subContainerClassName = ""
      override def inputField = super.inputField %("onkeydown" -> "return processKey(event , '%s')".format(saveButtonId))
      override def validations =
        valMinLen(1, "Name must not be empty") _ :: Nil
    }
  }

  private[this] val groupDescription = {
    new WBTextAreaField("Group description", nodeGroup.description) {
      override def setFilter = notNull _ :: trim _ :: Nil
      override def className = "form-control"
      override def labelClassName = ""
      override def subContainerClassName = ""
      override def validations =  Nil
      override def errorClassName = "field_errors paddscala"
    }
  }

  private[this] val groupStatic = {
    new WBRadioField(
        "Group type",
        Seq("dynamic", "static"),
        if(nodeGroup.isDynamic) "dynamic" else "static",
        {
           //how to display label ? Capitalize, and with a tooltip
          case "static" => <span class="" title="The list of member nodes is defined at creation and will not change automatically.">Static</span>
          case "dynamic" => <span class="" title="Nodes will be automatically added and removed so that the list of members always matches this group's search criteria.">Dynamic</span>
          case _ => NodeSeq.Empty // guarding against NoMatchE
       }
    ) {
      override def setFilter = notNull _ :: trim _ :: Nil
      override def className = ""
      override def labelClassName = ""
      override def subContainerClassName = ""
    }
  }

  private[this] val groupContainer = new WBSelectField("Group container",
      (categoryHierarchyDisplayer.getCategoriesHierarchy(rootCategory, None).map { case (id, name) => (id.value -> name)}),
      parentCategoryId.value) {
      override def className = "form-control"
      override def labelClassName = ""
      override def subContainerClassName = ""
  }

  private[this] val formTracker = new FormTracker(List(groupName, groupDescription, groupContainer, groupStatic))

  private[this] def updateFormClientSide() : JsCmd = {
    SetHtml(htmlIdCategory, showForm())
  }

  private[this] def error(msg:String) = <span class="error">{msg}</span>

  private[this] def onFailure : JsCmd = {
    formTracker.addFormError(error("There was problem with your request."))
    updateFormClientSide() & JsRaw("""scrollToElement("errorNotification","#groupDetails");""")
  }

  private[this] def onSubmit() : JsCmd = {
    // Since we are doing the submit from the component, it ought to exist
    searchNodeComponent.get match {
      case Full(req) =>
        query = req.getQuery
        srvList = req.getSrvList
      case eb:EmptyBox =>
        val f = eb ?~! "Error when trying to retrieve the current search state"
        logger.error(f.messageChain)
    }

    if(formTracker.hasErrors) {
      onFailure & onFailureCallback()
    } else {
      val optContainer = {
        val c = NodeGroupCategoryId(groupContainer.get)
        if(c == parentCategoryId) None
        else Some(c)
      }

      val newGroup = nodeGroup.copy(
          name = groupName.get
        , description = groupDescription.get
        //, container = container
        , isDynamic = groupStatic.get match { case "dynamic" => true ; case _ => false }
        , query = query
        , serverList =  srvList.getOrElse(Set()).map( _.id ).toSet
      )

      if(newGroup == nodeGroup && optContainer.isEmpty) {
        formTracker.addFormError(Text("There are no modifications to save"))
        onFailure & onFailureCallback()
      } else {
        displayConfirmationPopup(ModificationValidationPopup.Save, newGroup, optContainer)
      }
    }
  }

  private[this] def onSubmitDelete(): JsCmd = {
    displayConfirmationPopup(ModificationValidationPopup.Delete, nodeGroup, None)
  }

  /*
   * Create the confirmation pop-up
   */

  private[this] def displayConfirmationPopup(
      action     : ModificationValidationPopup.Action
    , newGroup   : NodeGroup
    , newCategory: Option[NodeGroupCategoryId]
  ) : JsCmd = {

    val optOriginal = Some(nodeGroup)

    val popup = {

      def successCallback(crId:ChangeRequestId) = if (workflowEnabled) {
        onSuccessCallback(Right(crId))
      } else {
        val updateCategory = newCategory.getOrElse(parentCategoryId)
        successPopup & onSuccessCallback(Left((newGroup,updateCategory))) &
        (if (action==ModificationValidationPopup.Delete)
           SetHtml(htmlId_item,NodeSeq.Empty)
        else
             Noop)
      }

      new ModificationValidationPopup(
          Right((newGroup, newCategory, optOriginal))
        , action
        , workflowEnabled
        , crId => JsRaw("$('#confirmUpdateActionDialog').bsModal('hide');") & successCallback(crId)
        , xml => JsRaw("$('#confirmUpdateActionDialog').bsModal('hide');") & onFailure
        , parentFormTracker = formTracker
      )

    }

    popup.popupWarningMessages match {
      case None =>
        popup.onSubmit
      case Some(_) =>
        SetHtml("confirmUpdateActionDialog", popup.popupContent) &
        JsRaw("""createPopup("confirmUpdateActionDialog")""")
    }
  }

  def createPopup(name:String) :JsCmd = {
    JsRaw(s"""createPopup("${name}");""")
  }
  private[this] def showCloneGroupPopup() : JsCmd = {
    val popupSnippet = new LocalSnippet[CreateCloneGroupPopup]
            popupSnippet.set(Full(new CreateCloneGroupPopup(
                Some(nodeGroup)
              , onSuccessCategory = displayACategory
              , onSuccessGroup = showGroupSection
            )))
    val nodeSeqPopup = popupSnippet.get match {
      case Failure(m, _, _) =>  <span class="error">Error: {m}</span>
      case Empty => <div>The component is not set</div>
      case Full(popup) => popup.popupContent()
    }
    SetHtml("createCloneGroupContainer", nodeSeqPopup) &
    createPopup("createCloneGroupPopup")
  }

  private[this] def displayACategory(category : NodeGroupCategory) : JsCmd = {
    refreshRightPanel(CategoryForm(category))
  }

  private[this] def refreshRightPanel(panel:RightPanel) : JsCmd = SetHtml(htmlId_item, setAndShowRightPanel(panel))

  /**
   *  Manage the state of what should be displayed on the right panel.
   * It could be nothing, a group edit form, or a category edit form.
   */
  private[this] def setAndShowRightPanel(panel:RightPanel) : NodeSeq = {
    panel match {
      case NoPanel => NodeSeq.Empty
      case GroupForm(group, catId) =>
        val form = new NodeGroupForm(htmlId_item, group, catId, rootCategory, workflowEnabled, onSuccessCallback)
        nodeGroupForm.set(Full(form))
        form.showForm()

      case CategoryForm(category) =>
        val form = new NodeGroupCategoryForm(htmlId_item, category, rootCategory) //, onSuccessCallback)
        nodeGroupCategoryForm.set(Full(form))
        form.showForm()
    }
  }

  private[this] def showGroupSection(group: NodeGroup, parentCategoryId: NodeGroupCategoryId) : JsCmd = {
    //update UI
    onSuccessCallback(Left((group, parentCategoryId)))&
    JsRaw("""this.window.location.hash = "#" + JSON.stringify({'groupId':'%s'})""".format(group.id.value))
  }

  private[this] def updateAndDisplayNotifications() : NodeSeq = {

    val notifications = formTracker.formErrors
    formTracker.cleanErrors

    if(notifications.isEmpty) {
      NodeSeq.Empty
    }
    else {
      val html =
        <div id="errorNotification" class="notify">
          <ul class="field_errors">{notifications.map( n => <li>{n}</li>) }</ul>
        </div>
      html
    }
  }

  private[this] def successPopup : JsCmd = {
    JsRaw(""" callPopupWithTimeout(200, "successConfirmationDialog")
    """)
  }

}
