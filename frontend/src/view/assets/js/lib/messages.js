(function(){

  var Messages = function (messageHead, messageContent, type, onClose, newestOnTop) {
        var $message,
            timeout,
            options = {
                timeout: {
                    'success': 3000,
                    'warning': 3000
                }
            };

    if (!type) {
      type = 'info';
    }

    if (newestOnTop === null) {
        newestOnTop = true;
    }

    timeout = options['timeout'][type || 'info'] || 0;

    // check if message is already displayed by comparing content and head
    _.each($('.notification'), function(message) {
      var $message = $(message),
          displayedMessageHead,
          displayedMessageContent;

      displayedMessageHead = $message.data('message-head');
      displayedMessageContent = $message.data('message-content');

      if (messageHead === displayedMessageHead && messageContent === displayedMessageContent) {
        $message.remove();
      }
    });

    $message = toastr[type](messageHead, messageContent, {timeOut: timeout, extendedTimeOut: timeout, onCloseClick: onClose, newestOnTop: newestOnTop});
    $message.data('message-head', messageHead);
    $message.data('message-content', messageContent);
  };

  var displayMessages = function(title, messages, type) {
    messages.forEach(function (text) {
      Messages(title, text, type);
    });
  };

  var JsonMessages = function (messages) {
    if (messages) {
      if (messages.success) {
        displayMessages(t("defaults.success"), messages.success, 'success');
      }

      if (messages.warning) {
        displayMessages(t("defaults.warning"), messages.warning, 'warning');
      }

      if (messages.alert) {
        displayMessages(t("defaults.error"), messages.alert, 'alert');
      }

      if (messages.fields) {
        $.each(messages.fields, function(name, errors) {
          if (errors) {
            displayMessages(t("defaults.error"), errors, 'alert');
          }
        });
      }
    }
  };

  AGN.Lib.Messages = Messages;
  AGN.Lib.JsonMessages = JsonMessages;
})();
