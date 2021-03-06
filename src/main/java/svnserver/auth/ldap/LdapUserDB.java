/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap;

import com.unboundid.ldap.sdk.*;
import com.unboundid.util.ssl.SSLUtil;
import org.eclipse.jgit.util.Base64;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.Authenticator;
import svnserver.auth.PlainAuthenticator;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.auth.ldap.config.LdapUserDBConfig;
import svnserver.config.ConfigHelper;
import svnserver.context.SharedContext;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

/**
 * LDAP authentication.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class LdapUserDB implements UserDB {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(LdapUserDB.class);

  @NotNull
  private final Collection<Authenticator> authenticators = Collections.singleton(new PlainAuthenticator(this));
  @NotNull
  private final LDAPConnectionPool pool;
  @NotNull
  private final LdapUserDBConfig config;
  @NotNull
  private final String baseDn;
  @Nullable
  private final String fakeMailSuffix;

  public LdapUserDB(@NotNull SharedContext context, @NotNull LdapUserDBConfig config) {
    try {
      URI ldapUri = URI.create(config.getConnectionUrl());
      this.baseDn = ldapUri.getPath().isEmpty() ? "" : ldapUri.getPath().substring(1);
      final ServerSet serverSet = createServerSet(context, config);
      final BindRequest bindRequest = config.getBind().createBindRequest();
      this.pool = new LDAPConnectionPool(serverSet, bindRequest, 1, config.getMaxConnections());
      this.fakeMailSuffix = createFakeMailSuffix(config);
      this.config = config;
    } catch (LDAPException e) {
      throw new IllegalStateException(e);
    }
  }

  @NotNull
  private static ServerSet createServerSet(@NotNull SharedContext context, @NotNull LdapUserDBConfig config) {
    final URI ldapUri = URI.create(config.getConnectionUrl());
    final SocketFactory factory;
    final int defaultPort;
    switch (ldapUri.getScheme().toLowerCase(Locale.ENGLISH)) {
      case "ldap":
        factory = null;
        defaultPort = 389;
        break;
      case "ldaps":
        factory = createSslFactory(context, config);
        defaultPort = 636;
        break;
      default:
        throw new IllegalStateException("Unknown ldap scheme: " + ldapUri.getScheme());
    }
    final String ldapHost = ldapUri.getHost();
    final int ldapPort = ldapUri.getPort() > 0 ? ldapUri.getPort() : defaultPort;
    return new SingleServerSet(ldapHost, ldapPort, factory);
  }

  @Nullable
  private static String createFakeMailSuffix(@NotNull LdapUserDBConfig config) {
    final String suffix = config.getFakeMailSuffix();
    if (suffix.isEmpty()) {
      return null;
    }
    return suffix.indexOf('@') < 0 ? '@' + suffix : suffix;
  }

  @NotNull
  private static SocketFactory createSslFactory(@NotNull SharedContext context, @NotNull LdapUserDBConfig config) {
    try {
      final String certPem = config.getLdapCertPem();
      if (certPem != null) {
        final File certFile = ConfigHelper.joinPath(context.getBasePath(), certPem);
        log.info("Loading CA certificate from: {}", certFile.getAbsolutePath());
        final byte[] cert = Files.readAllBytes(certFile.toPath());
        final TrustManager trustManager = createTrustManager(cert);
        return new SSLUtil(trustManager).createSSLSocketFactory();
      } else {
        log.info("CA certificate not defined. Using JVM default SSL context");
        return SSLContext.getDefault().getSocketFactory();
      }
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    } catch (IOException e) {
      throw new IllegalStateException("Can't load certificate file", e);
    }
  }

  @NotNull
  private static TrustManager createTrustManager(@NotNull byte[] pem) throws GeneralSecurityException {
    final TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    final KeyStore keystore = getKeyStoreFromDER(parseDERFromPEM(pem, "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----"));
    factory.init(keystore);

    final TrustManager[] trustManagers = factory.getTrustManagers();
    return new X509TrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        for (TrustManager trustManager : trustManagers) {
          ((X509TrustManager) trustManager).checkClientTrusted(x509Certificates, s);
        }
      }

      @Override
      public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        for (TrustManager trustManager : trustManagers) {
          ((X509TrustManager) trustManager).checkServerTrusted(x509Certificates, s);
        }
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }
    };
  }

  @NotNull
  private static KeyStore getKeyStoreFromDER(@NotNull byte[] certBytes) throws GeneralSecurityException {
    try {
      final CertificateFactory factory = CertificateFactory.getInstance("X.509");
      final KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
      keystore.load(null);
      keystore.setCertificateEntry("alias", factory.generateCertificate(new ByteArrayInputStream(certBytes)));
      return keystore;
    } catch (IOException e) {
      throw new KeyStoreException(e);
    }
  }

  @NotNull
  private static byte[] parseDERFromPEM(@NotNull byte[] pem, @NotNull String beginDelimiter, @NotNull String endDelimiter) throws GeneralSecurityException {
    final String data = new String(pem, StandardCharsets.ISO_8859_1);
    String[] tokens = data.split(beginDelimiter);
    if (tokens.length != 2) {
      throw new GeneralSecurityException("Invalid PEM certificate data. Delimiter not found: " + beginDelimiter);
    }
    tokens = tokens[1].split(endDelimiter);
    if (tokens.length != 2) {
      throw new GeneralSecurityException("Invalid PEM certificate data. Delimiter not found: " + endDelimiter);
    }
    return Base64.decode(tokens[0]);
  }

  @Override
  public void close() {
    this.pool.close();
  }

  @NotNull
  @Override
  public Collection<Authenticator> authenticators() {
    return authenticators;
  }

  @Override
  public User check(@NotNull String userName, @NotNull String password) throws SVNException {
    return findUser(userName, userDN -> pool.bindAndRevertAuthentication(userDN, password).getResultCode() == ResultCode.SUCCESS);
  }

  @Nullable
  @Override
  public User lookupByUserName(@NotNull String userName) throws SVNException {
    return findUser(userName, userDN -> true);
  }

  @Nullable
  @Override
  public User lookupByExternal(@NotNull String external) {
    return null;
  }

  private User findUser(@NotNull String userName, @NotNull LdapCheck ldapCheck) throws SVNException {
    try {
      final Filter filter;
      if (!config.getSearchFilter().isEmpty()) {
        filter = Filter.createANDFilter(
            Filter.create(config.getSearchFilter()),
            Filter.createEqualityFilter(config.getLoginAttribute(), userName)
        );
      } else {
        filter = Filter.createEqualityFilter(config.getLoginAttribute(), userName);
      }
      final SearchResult search = pool.search(baseDn, SearchScope.SUB, filter, config.getLoginAttribute(), config.getNameAttribute(), config.getEmailAttribute());
      if (search.getEntryCount() == 1) {
        final SearchResultEntry entry = search.getSearchEntries().get(0);
        final String login = getAttribute(entry, config.getLoginAttribute());
        if (login == null) {
          throw new IllegalStateException("Can't get login for user: " + userName);
        }
        if (ldapCheck.check(entry.getDN())) {
          final String realName = getAttribute(entry, config.getNameAttribute());
          String email = getAttribute(entry, config.getEmailAttribute());
          if (email == null && fakeMailSuffix != null) {
            email = login + fakeMailSuffix;
          }
          return User.create(login, realName != null ? realName : login, email, null);
        }
      }
      return null;
    } catch (LDAPException e) {
      if (e.getResultCode() == ResultCode.INVALID_CREDENTIALS) {
        return null;
      }
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_NO_PROVIDER, e.getMessage()), e);
    }
  }

  @Nullable
  private String getAttribute(@NotNull SearchResultEntry entry, @NotNull String name) {
    Attribute attribute = entry.getAttribute(name);
    return attribute == null ? null : attribute.getValue();
  }

  @FunctionalInterface
  private interface LdapCheck {
    boolean check(@NotNull String userDN) throws LDAPException;
  }
}
