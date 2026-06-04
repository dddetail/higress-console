import CodeEditor, { CodeEditorRef } from '@/components/CodeEditor';
import { Mode } from '@/interfaces/config';
import type { Oauth2ProviderDetailDetail } from '@/interfaces/user';
import { getHigressConfig, updateHigressConfig } from '@/services/system';
import {
  addProvider,
  deleteProvider,
  getSsoStatus,
  listProviders,
  setSsoStatus,
  updateProvider,
} from '@/services/oauth2';
import store from '@/store';
import { PageContainer } from '@ant-design/pro-layout';
import { PlusOutlined } from '@ant-design/icons';
import { useRequest } from 'ahooks';
import {
  Button,
  Form,
  Input,
  message,
  Modal,
  Popconfirm,
  Switch,
  Table,
  Tabs,
  Tag,
  Select,
  Space,
} from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

const { TabPane } = Tabs;
const { Option } = Select;

const PRESET_PROVIDERS = [
  { key: 'github', name: 'GitHub' },
  { key: 'gitlab', name: 'GitLab' },
];

const SystemSettings: React.FC = () => {
  const { t } = useTranslation();

  // ===== Higress Config Tab =====
  const [loaded, setLoaded] = useState<boolean>(false);
  const [configYaml, setConfigYaml] = useState<string>('');
  const [mode, setMode] = useState<string>(Mode.K8S);
  const [form] = Form.useForm();
  const codeEditorRef = useRef<CodeEditorRef>();

  const [configModel] = store.useModel('config');
  useEffect(() => {
    const properties = configModel ? configModel.properties : {};
    setMode(properties.mode || Mode.K8S);
  }, [configModel]);

  useRequest(getHigressConfig, {
    onSuccess: (result) => {
      setLoaded(true);
      setConfigYaml(result);
    },
  });

  const handleSubmitConfig = async () => {
    try {
      const updatedConfigYaml = await updateHigressConfig(configYaml);
      setConfigYaml(updatedConfigYaml);
      codeEditorRef.current?.pushContent(updatedConfigYaml);
      message.success(t('plugins.saveSuccess'));
    } catch (errInfo) {
      console.log('Update higress-config failed.', errInfo);
    }
  };

  // ===== SSO Config Tab =====
  const [ssoEnabled, setSsoEnabledState] = useState<boolean>(false);
  const [providers, setProviders] = useState<Oauth2ProviderDetail[]>([]);
  const [drawerVisible, setDrawerVisible] = useState<boolean>(false);
  const [editingProvider, setEditingProvider] = useState<Oauth2ProviderDetail | null>(null);
  const [providerForm] = Form.useForm();
  const [presetKey, setPresetKey] = useState<string>('');

  const loadSsoConfig = async () => {
    try {
      const [enabled, providerList] = await Promise.all([getSsoStatus(), listProviders()]);
      setSsoEnabledState(enabled);
      setProviders(providerList || []);
    } catch (e) {
      // ignore
    }
  };

  useEffect(() => {
    loadSsoConfig();
  }, []);

  const handleToggleSso = async (checked: boolean) => {
    try {
      await setSsoStatus(checked);
      setSsoEnabledState(checked);
      message.success(checked ? t('sso.enabledStatus') : t('sso.disabledStatus'));
    } catch (e: any) {
      message.error(e?.message || e?.data?.message || t('sso.enableSsoFirst'));
    }
  };

  const handleAddProvider = () => {
    setEditingProvider(null);
    setPresetKey('');
    providerForm.resetFields();
    providerForm.setFieldsValue({ enabled: true });
    setDrawerVisible(true);
  };

  const handleEditProvider = (record: Oauth2ProviderDetail) => {
    setEditingProvider(record);
    setPresetKey(record.isPreset ? record.providerKey : '');
    providerForm.setFieldsValue({
      ...record,
      clientSecret: '', // Don't show existing secret
    });
    setDrawerVisible(true);
  };

  const handleDeleteProvider = async (id: number) => {
    try {
      await deleteProvider(id);
      message.success(t('misc.deleteSuccess'));
      loadSsoConfig();
    } catch (e) {
      message.error(t('misc.deleteFailed'));
    }
  };

  const handleSaveProvider = async () => {
    try {
      const values = await providerForm.validateFields();
      // If preset, ensure providerKey is set
      if (presetKey) {
        values.providerKey = presetKey;
      }
      if (editingProvider) {
        await updateProvider(editingProvider.id, values);
        message.success(t('misc.updateSuccess'));
      } else {
        await addProvider(values);
        message.success(t('misc.addSuccess'));
      }
      setDrawerVisible(false);
      loadSsoConfig();
    } catch (e) {
      // validation error
    }
  };

  const handlePresetChange = (key: string) => {
    setPresetKey(key);
    if (key) {
      const preset = PRESET_PROVIDERS.find((p) => p.key === key);
      if (preset) {
        providerForm.setFieldsValue({
          name: preset.name,
          providerKey: key,
        });
      }
    }
  };

  const providerColumns = [
    {
      title: t('sso.providerName'),
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: t('sso.providerKey'),
      dataIndex: 'providerKey',
      key: 'providerKey',
    },
    {
      title: t('sso.clientId'),
      dataIndex: 'clientId',
      key: 'clientId',
      ellipsis: true,
    },
    {
      title: t('sso.enabledStatus'),
      dataIndex: 'enabled',
      key: 'enabled',
      render: (val: boolean) =>
        val ? <Tag color="green">{t('sso.enabledStatus')}</Tag> : <Tag>{t('sso.disabledStatus')}</Tag>,
    },
    {
      title: t('userManagement.actions'),
      key: 'actions',
      render: (_: any, record: Oauth2ProviderDetail) => (
        <Space>
          <a onClick={() => handleEditProvider(record)}>{t('sso.editProvider')}</a>
          <Popconfirm
            title={t('sso.deleteConfirm')}
            onConfirm={() => handleDeleteProvider(record.id)}
            okText={t('misc.confirm')}
            cancelText={t('misc.cancel')}
          >
            <a>{t('userManagement.deleteUser')}</a>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer>
      <Tabs defaultActiveKey="config">
        <TabPane tab={t('system.higress-config.title')} key="config">
          <Form
            form={form}
            style={{
              background: '#fff',
              padding: 16,
            }}
          >
            <Form.Item
              extra={
                t(
                  mode === Mode.K8S
                    ? 'system.higress-config.note_k8s'
                    : 'system.higress-config.note_standalone',
                )
              }
            >
              <CodeEditor
                defaultValue={configYaml}
                ref={codeEditorRef}
                onChange={(val) => {
                  setConfigYaml(val);
                }}
              />
            </Form.Item>
            <Form.Item style={{ marginBottom: 0 }}>
              <Button type="primary" disabled={!configYaml} onClick={handleSubmitConfig}>
                {t('misc.submit')}
              </Button>
            </Form.Item>
          </Form>
        </TabPane>

        <TabPane tab={t('sso.title')} key="sso">
          <div style={{ background: '#fff', padding: 24 }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: 24,
              }}
            >
              <div>
                <h3 style={{ margin: 0 }}>{t('sso.enabled')}</h3>
                <p style={{ color: 'rgba(0,0,0,0.45)', margin: '4px 0 0' }}>{t('sso.enabledTip')}</p>
              </div>
              <Switch checked={ssoEnabled} onChange={handleToggleSso} />
            </div>

            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: 16,
              }}
            >
              <h3 style={{ margin: 0 }}>{t('sso.providerList')}</h3>
              <Button type="primary" icon={<PlusOutlined />} onClick={handleAddProvider}>
                {t('sso.addProvider')}
              </Button>
            </div>

            <Table
              columns={providerColumns}
              dataSource={providers}
              rowKey="id"
              pagination={false}
            />
          </div>
        </TabPane>
      </Tabs>

      <Modal
        title={editingProvider ? t('sso.editProvider') : t('sso.addProvider')}
        open={drawerVisible}
        onOk={handleSaveProvider}
        onCancel={() => setDrawerVisible(false)}
        width={600}
        okText={t('misc.confirm')}
        cancelText={t('misc.cancel')}
      >
        <Form form={providerForm} layout="vertical">
          <Form.Item label={t('sso.presetTemplate')}>
            <Select
              allowClear
              value={presetKey || undefined}
              onChange={handlePresetChange}
              placeholder={t('sso.customProvider')}
            >
              {PRESET_PROVIDERS.map((p) => (
                <Option key={p.key} value={p.key}>
                  {p.name}
                </Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            name="name"
            label={t('sso.providerName')}
            rules={[{ required: true }]}
          >
            <Input />
          </Form.Item>

          <Form.Item
            name="providerKey"
            label={t('sso.providerKey')}
            rules={[{ required: true }]}
          >
            <Input disabled={!!editingProvider} />
          </Form.Item>

          {!presetKey && (
            <>
              <Form.Item
                name="authorizationUrl"
                label={t('sso.authorizationUrl')}
                rules={[{ required: !presetKey }]}
              >
                <Input />
              </Form.Item>
              <Form.Item
                name="tokenUrl"
                label={t('sso.tokenUrl')}
                rules={[{ required: !presetKey }]}
              >
                <Input />
              </Form.Item>
              <Form.Item
                name="userInfoUrl"
                label={t('sso.userInfoUrl')}
                rules={[{ required: !presetKey }]}
              >
                <Input />
              </Form.Item>
            </>
          )}

          <Form.Item name="scope" label={t('sso.scope')}>
            <Input />
          </Form.Item>

          <Form.Item
            name="clientId"
            label={t('sso.clientId')}
            rules={[{ required: true }]}
          >
            <Input />
          </Form.Item>

          <Form.Item
            name="clientSecret"
            label={t('sso.clientSecret')}
            rules={editingProvider ? [] : [{ required: true }]}
          >
            <Input.Password placeholder={editingProvider ? t('sso.clientSecretPlaceholder') : ''} />
          </Form.Item>

          <Form.Item name="iconUrl" label={t('sso.iconUrl')}>
            <Input />
          </Form.Item>

          <Form.Item name="enabled" label={t('sso.enabledStatus')} valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default SystemSettings;
