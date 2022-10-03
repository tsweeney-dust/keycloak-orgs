package io.phasetwo.service.auth;

import static io.phasetwo.service.Orgs.*;

import com.google.auto.service.AutoService;
import io.phasetwo.service.model.OrganizationModel;
import io.phasetwo.service.model.OrganizationProvider;
import javax.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.broker.util.ExistingUserInfo;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.events.Errors;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.messages.Messages;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.util.JsonSerialization;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.models.KeycloakSessionFactory;
import java.util.Map;
import org.keycloak.models.AuthenticationFlowModel;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.provider.ProviderFactory;

/** */
@JBossLog
@AutoService(AuthenticatorFactory.class)
public class OrgAddUserAuthenticatorFactory extends BaseAuthenticatorFactory
    implements DefaultAuthenticator {

  public static final String PROVIDER_ID = "ext-auth-org-add-user";

  public OrgAddUserAuthenticatorFactory() {
    super(PROVIDER_ID);
  }

  @Override
  public void authenticate(AuthenticationFlowContext context) {}
  
  @Override
  public void action(AuthenticationFlowContext context) {
    BrokeredIdentityContext brokerContext = PostOrgAuthFlow.getBrokeredIdentityContext(context);
    if (!PostOrgAuthFlow.brokeredIdpEnabled(context, brokerContext)) return;

    Map<String,String> idpConfig = brokerContext.getIdpConfig().getConfig();
    if (idpConfig != null && idpConfig.containsKey(ORG_OWNER_CONFIG_KEY)) {
      OrganizationProvider orgs = context.getSession().getProvider(OrganizationProvider.class);
      OrganizationModel org = orgs.getOrganizationById(context.getRealm(), idpConfig.get(ORG_OWNER_CONFIG_KEY));
      if (!org.hasMembership(context.getUser())) {
        log.infof("granting membership to %s for user %s", org.getName(), context.getUser().getUsername());
        org.grantMembership(context.getUser());
        // TODO default roles from config??
      }
    } else {
      log.infof("No organization owns IdP %s", brokerContext.getIdpConfig().getAlias());
    }
  }

  @Override
  public boolean requiresUser() {
    return true;
  }

  @Override
  public Authenticator create(KeycloakSession session) {
    return this;
  }

  @Override
  public boolean isUserSetupAllowed() {
    return false;
  }

  @Override
  public String getHelpText() {
    return "Adds a user to an organization if an organization-owned IdP was used to log in. Use only in Post Login Flows.";
  }

  @Override
  public String getDisplayType() {
    return "Add User to Org";
  }

  @Override
  public String getReferenceCategory() {
    return "Post Broker";
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    factory.register(
        (ProviderEvent ev) -> {
          if (ev instanceof RealmModel.RealmPostCreateEvent) {
            PostOrgAuthFlow.realmPostCreate((RealmModel.RealmPostCreateEvent)ev, PROVIDER_ID);
          }
        });
  }
}
