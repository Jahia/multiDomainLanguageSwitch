(function () {
    var contextPath = window.contextJsParameters.contextPath;

    window.jahia.i18n.loadNamespaces('multi-domain-language-switch').then(function () {
        window.jahia.uiExtender.registry.add('adminRoute', 'languageUrlSettings', {
            targets: ['administration-sites:70'],
            requiredPermission: 'siteAdminLanguageUrlMapping',
            label: 'multi-domain-language-switch:lsm.siteSettings.title',
            icon: window.jahia.moonstone.toIconComponent('Link'),
            isSelectable: true,
            iframeUrl: contextPath + '/cms/editframe/default/$lang/sites/$site-key.languageUrlSettings.html'
        });
    });
}());
