AGN.Lib.Controller.new('mailing-content-editor-controller', function () {

  // libs
  var Modal = AGN.Lib.Modal;
  var Confirm = AGN.Lib.Confirm;
  var Template = AGN.Lib.Template;

  // configuration
  var currentDynTag;
  var targetGroups;
  var saveUrl;
  var DynTagObject;
  var showHTMLEditor;

  // ui elements
  var $selectableContainer;
  var $orderedArea;
  var $targetGroups;
  var $interestGroups;
  var $editableArea;
  var preparedEntryTemplate;

  // constants
  var EVENT_NAMESPACE = '.mailingContentNamespace';
  var CONTENT_BLOCK_SELECTOR = '.l-mailing-content-entry';
  var ID_PREFIX = 'content_entry_';

  this.addDomInitializer('mailing-content-editor-initializer', function ($modal) {
    var config = Modal.get($modal);
    currentDynTag = config.dynTag;
    targetGroups = config.targetGroups;
    saveUrl = config.saveUrl;
    DynTagObject = config.DynTagObject;
    showHTMLEditor = config.showHTMLEditor;

    $selectableContainer = $('#content_area');
    $orderedArea = $('#ordered_area');
    $targetGroups = $('#target_groups');
    $interestGroups = $('#interest_groups');
    $editableArea = $('#editable_area');
    preparedEntryTemplate = Template.prepare('mailing-content-entry-template');

    initSortable();
    initButtonClickListeners();
    initDynContent(currentDynTag);
    initInterestGroup(currentDynTag.interestGroup);
    if (currentDynTag.contentBlocks.length > 0) {
      var firstContentBlock = currentDynTag.contentBlocks[0];
      selectContentBlock(firstContentBlock);
    } else {
      createNewTag();
    }
    initTargetGroupListeners();
    initInterestListeners();
  });

  this.addAction({
    click: 'selectContentEntry'
  }, function () {
    var $current = $(this.el);
    var contentBlockId = getElementId($current);
    var contentBlock = currentDynTag.getContentBlockById(contentBlockId);
    selectContentBlock(contentBlock);
  });

  this.addAction({
    click: 'deleteContentEntry'
  }, function () {
    this.event.stopPropagation();
    var $current = $(this.el).parent();
    var id = getElementId($current);
    var $nextSelected = $current.next(CONTENT_BLOCK_SELECTOR);
    $nextSelected = $nextSelected.length > 0 ? $nextSelected : $current.prev(CONTENT_BLOCK_SELECTOR);
    currentDynTag.remove(id);
    $current.remove();

    if (currentDynTag.contentBlocks.length > 0) {
      // picks one of the neighbours
      var nextSelectedId = getElementId($nextSelected);
      var nextSelected = currentDynTag.getContentBlockById(nextSelectedId);
      selectContentBlock(nextSelected);
    } else {
      // just clears everything
      selectContentBlock();
      lockEditableArea();
    }
  });

  this.addAction({
    click: 'createEnlargedContentEditorModal'
  }, function () {
    getEditorsContent();

    var selectedContentBlockId = getSelectedContentBlockId();
    if (selectedContentBlockId) {
      var contentBlock = currentDynTag.getContentBlockById(selectedContentBlockId);
      var promise = Confirm.createFromTemplate(
        {
          content: contentBlock.content,
          selectedContentBlock: selectedContentBlockId,
          editorType: getActiveEditor(),
          isFullHtmlTags: currentDynTag.name == 'HTML-Version',
          showHTMLEditor: showHTMLEditor
        }, 'enlarged-content-editor-template');

      promise.done(function (response) {
        var updatedContentBlock = currentDynTag.changeContent(selectedContentBlockId, response.content);
        setEditorsContent(updatedContentBlock.content);
        synchronizeEditorTab(response.editorType);
      });
    }
  });

  this.addAction({
    click: 'addDynContent'
  }, function () {
    createNewTag();
  });

  this.addAction({
    click: 'saveDynTag'
  }, function () {
    getEditorsContent();

    if (validateDynTag()) {
      var $currentElement = $(this.el);
      $.ajax({
        type: "POST",
        url: saveUrl,
        dataType: "json",
        contentType: 'application/json',
        data: JSON.stringify(currentDynTag),
        success: function (response) {
          var deferred = Confirm.get($currentElement);
          AGN.Lib.JsonMessages(response.popups);
          if (response.success) {
            deferred.positive(new DynTagObject(response.data));
            closeModal();
          } else {
            deferred.negative()
          }
        }
      });
    }
  });

  var validateDynTag = function()  {
    // check for duplications
    var duplicatedContentBlocks = currentDynTag.contentBlocks.map(function (content) {
      return content.targetId;
    }).filter(function (targetId, index, targetIds) {
      return targetIds.indexOf(targetId, index+1) > -1
    }, []);

    if (duplicatedContentBlocks.length) {
      AGN.Lib.Messages(t("Error"), t("mailing.validation.target_group_duplicated"), "alert");
      return false;
    }

    // check for 'all recipients at the very end'
    var allRecipientsContentBlock = currentDynTag.contentBlocks.filter(function (block) {
      return block.targetId === 0;
    }, []);

    if (allRecipientsContentBlock.length !== currentDynTag.contentBlocks.length) {
      if (allRecipientsContentBlock.length && (allRecipientsContentBlock[0].index < currentDynTag.contentBlocks.length)) {
        AGN.Lib.Messages(t("Error"), t("mailing.validation.all_recipients_not_last"), "alert");
        return false;
      }
    }

    return true;
  };

  var moveUp = function () {
    var $current = getSelectedContentBlock();
    var $previous = $current.prev(CONTENT_BLOCK_SELECTOR);
    if ($previous.length !== 0) {
      $current.insertBefore($previous);
      currentDynTag.moveUpContentBlock(getElementId($current));
    }
  };

  var moveDown = function () {
    var $current = getSelectedContentBlock();
    var $next = $current.next(CONTENT_BLOCK_SELECTOR);
    if ($next.length !== 0) {
      $current.insertAfter($next);
      currentDynTag.moveDownContentBlock(getElementId($current));
    }
  };

  var initButtonClickListeners = function () {
    var ESC =  27;
    var UP = 38;
    var DOWN = 40;

    $(document).off('keydown' + EVENT_NAMESPACE);
    $(document).on('keydown' + EVENT_NAMESPACE, function (event) {
      event.stopPropagation();
      switch (event.which) {
        case ESC:
          closeModal();
          break;

        case UP:
          moveUp();
          break;

        case DOWN:
          moveDown();
          break;

        default:
          return;
      }
    });
  };

  var initSortable = function () {
    $selectableContainer.sortable({
      opacity: 0.4,
      delay: 100,
      scroll: true,
      scrollSensitivity: 40,
      scrollSpeed: 15,
      cursor: "move",
      items: CONTENT_BLOCK_SELECTOR,
      helper: 'clone',
      containment: $orderedArea,
      appendTo: $orderedArea,
      start: function (event, ui) {
        ui.item.css('cursor', 'move');
      },
      stop: function (event, ui) {
        ui.item.removeAttr('style');
        var sortableBlock = $(event.target);
        var newOrder = sortableBlock.sortable('toArray');
        var newOrderIds = newOrder.map(function (id) {
          return id.replace(ID_PREFIX, '');
        });
        currentDynTag.changeOrder(newOrderIds);
      }
    });
  };

  var getActiveEditor = function() {
    var $wysiwygEditorBlock = $('#tab-content-wysiwyg');
    if ($wysiwygEditorBlock.is(":visible")) {
      return 'wysiwyg';
    }

    var $htmlEditorBlock = $('#contentEditor');
    if ($htmlEditorBlock.is(":visible")) {
      return 'html';
    }

    return '';
  };

  var synchronizeEditorTab = function(editorType) {
    if (!editorType || editorType === '') {
      return;
    }

    var tabLink = $('[data-toggle-tab$="' + editorType + '"]');
    if (tabLink) {
      tabLink.click();
    }
  };

  var setEditorsContent = function (content) {
    var $wysiwygEditorBlock = $('#tab-content-wysiwyg');
    var $htmlEditorBlock = $('#contentEditor');
    if ($wysiwygEditorBlock.is(":visible")) {
      var editor = CKEDITOR.instances['content'];
      if (editor.status === 'ready') {
        editor.setData(content)
      } else {
        editor.on("instanceReady", function (event) {
          event.editor.setData(content);
        });
      }
    }
    if ($htmlEditorBlock.is(":visible")) {
      ace.edit("contentEditor").setValue(content);
    }
  };

  var getEditorsContent = function() {
    var selectedContentBlockId = getSelectedContentBlockId();

    if (selectedContentBlockId > 0) {
      var $wysiwygEditorBlock = $('#tab-content-wysiwyg');
      var $htmlEditorBlock = $('#contentEditor');
      var content;

      if ($wysiwygEditorBlock.is(":visible")) {
        var wysiwygEditor = CKEDITOR.instances['content'];
        content = wysiwygEditor.getData();
      }

      if ($htmlEditorBlock.is(":visible")) {
        var htmlEditor = ace.edit("contentEditor");
        content = htmlEditor.getValue();
      }

      if (content !== undefined) {
        currentDynTag.changeContent(selectedContentBlockId, content);
      }
    }
  };

  var initDynContent = function (dynTag) {
    dynTag.contentBlocks.forEach(function (contentBlock) {
      var $orderedStub = $orderedArea.find('.l-stub');
      var targetGroup = getTargetGroupById(contentBlock.targetId);
      var html = preparedEntryTemplate({name: targetGroup.targetName, id: ID_PREFIX + contentBlock.uniqueId});
      $orderedStub.before(html);
    });
  };

  var initTargetGroup = function (id) {
    $targetGroups.val(id).trigger("change");
  };

  var initTargetGroupListeners = function () {
    $targetGroups.off('change' + EVENT_NAMESPACE);
    $targetGroups.on('change' + EVENT_NAMESPACE, function (event) {
      var newTargetGroupId = parseInt($(event.target).val());
      var selectedContentBlockId = getSelectedContentBlockId();
      if (selectedContentBlockId > 0) {
        var updatedContentBlock = currentDynTag.changeTargetGroup(selectedContentBlockId, newTargetGroupId);
        updateSelectedContentBlock(updatedContentBlock);
      }
    })
  };

  var initInterestGroup = function (key) {
    $interestGroups.val(key).trigger("change");
  };

  var initInterestListeners = function () {
    $interestGroups.off('change' + EVENT_NAMESPACE);
    $interestGroups.on('change' + EVENT_NAMESPACE, function (event) {
      var newInterestGroupKey = $(event.target).val();
      currentDynTag.changeInterestGroup(newInterestGroupKey);
    })
  };

  var updateSelectedContentBlock = function (contentBlock) {
    var $dynTag = findElementById(contentBlock.uniqueId);
    if ($dynTag.length > 0) {
      var targetGroup = getTargetGroupById(contentBlock.targetId);
      $dynTag.find('.l-name').children().text(targetGroup.targetName);
    }
  };

  var getSelectedContentBlock = function(){
    return $orderedArea.find(CONTENT_BLOCK_SELECTOR + '.selected');
  };

  var getSelectedContentBlockId = function () {
    var $selectedDynTag = getSelectedContentBlock();
    if ($selectedDynTag.length > 0) {
      return getElementId($selectedDynTag);
    }
  };

  var markSelected = function (contentBlockId) {
    $orderedArea.children().removeClass('selected');
    var $clickedBlock = findElementById(contentBlockId, $orderedArea);
    $clickedBlock.addClass('selected');
  };

  var getTargetGroupById = function (id) {
    var filteredTargetGroups = targetGroups.filter(function (targetGroup) {
      return targetGroup.id === id;
    });

    return filteredTargetGroups[0];
  };

  var findElementById = function(id, $parent) {
    $parent = $parent ? $parent : $(document);
    return $parent.find('#' + ID_PREFIX + id)
  };

  var getElementId = function($element) {
    return parseInt($element.attr('id').replace(ID_PREFIX, ''));
  };

  var closeModal = function() {
    $('.modal').modal('toggle');
  };

  var selectContentBlock = function(contentBlock) {
    getEditorsContent();

    if (contentBlock !== undefined) {
      markSelected(contentBlock.uniqueId);
      setEditorsContent(contentBlock.content);
      initTargetGroup(contentBlock.targetId);
    } else {
      setEditorsContent('');
      initTargetGroup(0);
    }
  };

  var createNewTag = function () {
    var $orderedStub = $orderedArea.find('.l-stub');
    var contentBlock = currentDynTag.createNewContentBlock();
    var targetGroup = getTargetGroupById(contentBlock.targetId);
    var html = preparedEntryTemplate({name: targetGroup.targetName, id: ID_PREFIX + contentBlock.uniqueId});
    $orderedStub.before(html);
    unlockEditableArea();
    selectContentBlock(contentBlock);
  };

  var lockEditableArea = function() {
    if ($editableArea.is(":visible")) {
      $editableArea.hide();
    }
  };

  var unlockEditableArea = function() {
    if ($editableArea.is(":hidden")) {
      $editableArea.show();
    }
  };
});
