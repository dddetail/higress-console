import logo from '@/assets/logo.png';
import LanguageDropdown from '@/components/LanguageDropdown';
import { LOGIN_PROMPT, SYSTEM_INITIALIZED } from '@/interfaces/config';
import type { LoginParams, UserInfo, Oauth2Provider } from '@/interfaces/user';
import { login } from '@/services';
import { getEnabledProviders } from '@/services/oauth2';
import store from '@/store';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { LoginForm, ProFormCheckbox, ProFormText } from '@ant-design/pro-form';
import { Alert, Button, Divider, message, Space } from 'antd';
import { history, useAuth, useNavigate } from 'ice';
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import styles from './index.module.css';

const Login: React.FC = () => {
  const { t } = useTranslation();

  const [loginPrompt, setLoginPrompt] = useState<string>();
  const [ssoProviders, setSsoProviders] = useState<Oauth2Provider[]>([]);
  const [oauthError, setOauthError] = useState<string>();
  const [, userDispatcher] = store.useModel('user');
  const [configModel] = store.useModel('config');
  const [, setAuth] = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    const properties = configModel ? configModel.properties : {};
    if (!properties[SYSTEM_INITIALIZED]) {
      navigate('/init', { replace: true });
      return;
    }
    setLoginPrompt(properties[LOGIN_PROMPT]);

    // Check for OAuth error from callback redirect
    const urlParams = new URL(window.location.href).searchParams;
    const error = urlParams.get('oauth_error');
    if (error) {
      setOauthError(decodeURIComponent(error));
      window.history.replaceState({}, '', '/login');
    }

    // Load enabled SSO providers
    getEnabledProviders()
      .then((providers) => {
        if (providers && providers.length > 0) {
          setSsoProviders(providers);
        }
      })
      .catch(() => {
        // SSO not configured, ignore
      });
  }, [configModel]);

  async function updateUserInfo(user: UserInfo) {
    userDispatcher.updateCurrentUser(user);
  }

  async function handleSubmit(values: LoginParams) {
    try {
      const user = await login(values);
      // We only support admin role at the moment.
      user.type = 'admin';
      message.success(t('login.loginSuccess'));
      setAuth({
        admin: user.type === 'admin',
        user: user.type === 'user',
      });
      await updateUserInfo(user);
      const urlParams = new URL(window.location.href).searchParams;
      let redirectUrl = urlParams.get('redirect');
      if (!redirectUrl || redirectUrl === '/login') {
        redirectUrl = '/';
      }
      history?.push(redirectUrl);
      return;
    } catch (error) {
      message.error(t('login.loginFailed'));
    }
  }

  function handleSsoLogin(providerKey: string) {
    window.location.href = `/api/oauth2/authorization/${providerKey}`;
  }

  return (
    <div className={styles.container}>
      <div className={styles['language-dropdown']}>
        <LanguageDropdown />
      </div>
      <LoginForm
        title=""
        logo={<img alt="logo" src={logo} />}
        subTitle=""
        onFinish={async (values) => {
          await handleSubmit(values as LoginParams);
        }}
        submitter={{
          searchConfig: {
            submitText: t('login.buttonText'),
          },
        }}
      >
        <ProFormText
          name="username"
          fieldProps={{
            size: 'large',
            prefix: <UserOutlined className={'prefixIcon'} />,
          }}
          placeholder={t('login.usernamePlaceholder')}
          rules={[
            {
              required: true,
              message: t('login.usernameRequired'),
            },
          ]}
        />
        <ProFormText.Password
          name="password"
          fieldProps={{
            size: 'large',
            prefix: <LockOutlined className={'prefixIcon'} />,
          }}
          placeholder={t('login.passwordPlaceholder')}
          rules={[
            {
              required: true,
              message: t('login.passwordRequired'),
            },
          ]}
        />
        {loginPrompt && (
          <div
            style={{
              marginBottom: 24,
              textAlign: 'center',
              whiteSpace: 'pre-wrap',
            }}
          >
            {loginPrompt}
          </div>
        )}
        <div
          style={{
            marginBottom: 24,
          }}
        >
          <ProFormCheckbox noStyle name="autoLogin">
            {t('login.autoLogin')}
          </ProFormCheckbox>
          <a
            style={{
              float: 'right',
            }}
          >
            {t('login.forgotPassword')}
          </a>
        </div>
      </LoginForm>

      {oauthError && (
        <Alert
          type="error"
          message={oauthError}
          style={{ maxWidth: 328, margin: '0 auto 16px' }}
          closable
          onClose={() => setOauthError(undefined)}
        />
      )}

      {ssoProviders.length > 0 && (
        <>
          <Divider style={{ maxWidth: 328, margin: '16px auto' }}>
            {t('sso.orDivider')}
          </Divider>
          <div className={styles['sso-buttons']}>
            <p className={styles['sso-label']}>{t('sso.ssoLogin')}</p>
            <Space direction="vertical" style={{ width: '100%', maxWidth: 328 }}>
              {ssoProviders.map((provider) => (
                <Button
                  key={provider.providerKey}
                  block
                  size="large"
                  onClick={() => handleSsoLogin(provider.providerKey)}
                  icon={provider.iconUrl ? (
                    <img src={provider.iconUrl} alt={provider.name} style={{ width: 18, height: 18 }} />
                  ) : undefined}
                >
                  {t('sso.loginWith', { name: provider.name })}
                </Button>
              ))}
            </Space>
          </div>
        </>
      )}
    </div>
  );
};

export default Login;
