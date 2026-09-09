import {deleteTestSite} from '../support/lsm';

describe('Teardown', () => {
    before(() => {
        cy.login();
    });

    it('deletes the test site', () => {
        deleteTestSite();
    });
});
