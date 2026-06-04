/* eslint-disable */
// @ts-nocheck
import { ConsumerDetail, ConsumerCreateRequest, CredentialType, ServiceSourceFormProps as FormProps } from '@/interfaces/consumer';
import { addConsumer, deleteConsumer, getConsumers, updateConsumer, listConsumerMembers, addConsumerMembers, removeConsumerMember } from '@/services/consumer';
import { listUsers } from '@/services/user';
import { ExclamationCircleOutlined, RedoOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-layout';
import { useRequest } from 'ahooks';
import { Button, Drawer, Form, Input, message, Modal, Select, Space, Table, Tag } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import ConsumerForm from './components/ConsumerForm';

interface FormRef {
  reset: () => void;
  handleSubmit: () => Promise<FormProps>;
}

const ConsumerList: React.FC = () => {
  const { t } = useTranslation();
  const columns = [
    {
      title: t('consumer.columns.name'),
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
    },
    {
      title: t('consumer.columns.nameCn'),
      dataIndex: 'nameCn',
      key: 'nameCn',
      ellipsis: true,
      render: (val: string) => val || '-',
    },
    {
      title: t('consumer.columns.shortName'),
      dataIndex: 'shortName',
      key: 'shortName',
    },
    {
      title: t('consumer.columns.authMethods'),
      dataIndex: 'credentials',
      key: 'credentials',
      render: (value) => {
        if (!Array.isArray(value) || !value.length) {
          return '-';
        }
        const supportedCredentialTypes = [];
        value.forEach(function (credential) {
          if (credential.type && supportedCredentialTypes.indexOf(credential.type) === -1) {
            supportedCredentialTypes.push(credential.type);
          }
        });
        if (supportedCredentialTypes.length === 0) {
          return '-';
        }
        supportedCredentialTypes.sort();
        return (
          <>
            {
              supportedCredentialTypes.map(function (type) {
                const credentialType = Object.values(CredentialType).find(t => t.enabled && t.key === type)
                  || { key: type, displayName: type, displayColor: 'black' };
                return (<Tag color={credentialType.displayColor} key={credentialType.key}>{credentialType.displayName}</Tag>);
              })
            }
          </>
        );
      },
    },
    {
      title: t('consumer.columns.memberCount'),
      key: 'memberCount',
      width: 100,
      align: 'center' as const,
      render: (_: any, record: ConsumerDetail) => (
        <a onClick={() => onShowMemberDrawer(record)}>
          {record.members?.length ?? 0}
        </a>
      ),
    },
    {
      title: t('misc.actions'),
      dataIndex: 'action',
      key: 'action',
      width: 140,
      align: 'center' as const,
      render: (_, record) => (
        <Space size="small">
          <a onClick={() => onEditDrawer(record)}>{t('misc.edit')}</a>
          <a onClick={() => onShowModal(record)}>{t('misc.delete')}</a>
        </Space>
      ),
    },
  ];

  const [form] = Form.useForm();
  const formRef = useRef<FormRef>(null);
  const [allConsumers, setAllConsumers] = useState<ConsumerDetail[]>([]);
  const [keyword, setKeyword] = useState('');
  const [keySearch, setKeySearch] = useState('');
  const [currentConsumer, setCurrentConsumer] = useState<ConsumerDetail>({} as ConsumerDetail);
  const [openDrawer, setOpenDrawer] = useState(false);
  const [openModal, setOpenModal] = useState(false);
  const [confirmLoading, setConfirmLoading] = useState(false);

  // Member management state
  const [memberDrawerVisible, setMemberDrawerVisible] = useState(false);
  const [memberConsumer, setMemberConsumer] = useState<ConsumerDetail | null>(null);
  const [consumerMembers, setConsumerMembers] = useState<string[]>([]);
  const [addMemberVisible, setAddMemberVisible] = useState(false);
  const [selectedUsernames, setSelectedUsernames] = useState<string[]>([]);
  const [allUsers, setAllUsers] = useState<any[]>([]);

  const { loading, run, refresh } = useRequest(getConsumers, {
    manual: true,
    onSuccess: (result) => {
      const consumers = (result || []) as ConsumerDetail[];
      consumers.sort((i1, i2) => {
        return i1.name.localeCompare(i2.name);
      })
      consumers.forEach(c => c.key = c.key || c.name);
      setAllConsumers(consumers);
    },
  });

  const { loading: membersLoading, run: fetchMembers } = useRequest(
    (name: string) => listConsumerMembers(name),
    { manual: true, onSuccess: (result) => setConsumerMembers(result || []) },
  );

  const { run: fetchUsers } = useRequest(listUsers, {
    manual: true,
    onSuccess: (result) => setAllUsers((result || []) as any[]),
  });

  useEffect(() => {
    run({});
  }, []);

  const onEditDrawer = (consumer: ConsumerDetail) => {
    setCurrentConsumer(consumer);
    setOpenDrawer(true);
  };

  const onShowDrawer = () => {
    setOpenDrawer(true);
    setCurrentConsumer(null);
  };

  const handleDrawerOK = async () => {
    const values: FormProps = formRef.current ? await formRef.current.handleSubmit() : {} as FormProps;
    if (!values) {
      return;
    };

    try {
      if (currentConsumer) {
        await updateConsumer(currentConsumer.name, values as ConsumerCreateRequest);
      } else {
        await addConsumer(values as ConsumerCreateRequest);
      }
      setOpenDrawer(false);
      formRef.current && formRef.current.reset();
      refresh();
    } catch (errInfo) {
      console.log('Save failed: ', errInfo);
    }
  };

  const handleDrawerCancel = () => {
    setOpenDrawer(false);
    formRef.current && formRef.current.reset();
    setCurrentConsumer(null);
  };

  const onShowModal = (consumer: ConsumerDetail) => {
    setCurrentConsumer(consumer);
    setOpenModal(true);
  };

  const handleModalOk = async () => {
    setConfirmLoading(true);
    try {
      await deleteConsumer(currentConsumer.name);
      message.success(t("consumer.deleteSuccess"));
    } catch (error) { }
    setConfirmLoading(false);
    setOpenModal(false);
    refresh();
  };

  const handleModalCancel = () => {
    setOpenModal(false);
    setCurrentConsumer(null);
  };

  const handleReset = () => {
    setKeyword('');
    setKeySearch('');
    form.resetFields();
  };

  // Member management handlers
  const onShowMemberDrawer = (consumer: ConsumerDetail) => {
    setMemberConsumer(consumer);
    setConsumerMembers(consumer.members || []);
    setMemberDrawerVisible(true);
    fetchUsers();
  };

  const handleRemoveMember = async (username: string) => {
    if (!memberConsumer) return;
    try {
      await removeConsumerMember(memberConsumer.name, username);
      fetchMembers(memberConsumer.name);
      refresh();
    } catch (e) {
      // handled by interceptor
    }
  };

  const handleAddMembers = async () => {
    if (!memberConsumer || selectedUsernames.length === 0) return;
    try {
      await addConsumerMembers(memberConsumer.name, selectedUsernames);
      setAddMemberVisible(false);
      setSelectedUsernames([]);
      fetchMembers(memberConsumer.name);
      refresh();
    } catch (e) {
      // handled by interceptor
    }
  };

  const dataSource = React.useMemo(() => {
    return allConsumers.filter((item) => {
      if (keyword && !item.name.toLowerCase().includes(keyword.toLowerCase())) {
        return false;
      }
      if (keySearch && !item.credentials?.some(c => JSON.stringify(c).toLowerCase().includes(keySearch.toLowerCase()))) {
        return false;
      }
      return true;
    });
  }, [allConsumers, keyword, keySearch]);

  const availableUsers = allUsers.filter(
    (u: any) => !consumerMembers.some((m) => m === u.name),
  );

  return (
    <PageContainer>
      <Form
        form={form}
        style={{
          background: '#fff',
          padding: '24px',
          marginBottom: 16,
        }}
        layout="inline"
      >
        <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space wrap size={24}>
            <Form.Item name="keyword" label={t('consumer.columns.name')} style={{ marginBottom: 0 }}>
              <Input
                placeholder={t('consumer.columns.name')}
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                allowClear
              />
            </Form.Item>
            <Form.Item name="keySearch" label={t('consumer.key')} style={{ marginBottom: 0 }}>
              <Input
                placeholder={t('consumer.key')}
                value={keySearch}
                onChange={(e) => setKeySearch(e.target.value)}
                allowClear
              />
            </Form.Item>
            <Form.Item style={{ marginBottom: 0 }}>
              <Space>
                <Button onClick={handleReset}>{t('misc.reset')}</Button>
              </Space>
            </Form.Item>
          </Space>
          <Space>
            <Button
              type="primary"
              onClick={onShowDrawer}
            >
              {t('consumer.create')}
            </Button>
            <Button
              icon={<RedoOutlined />}
              onClick={refresh}
            />
          </Space>
        </Space>
      </Form>
      <Table
        loading={loading}
        dataSource={dataSource}
        columns={columns}
        pagination={{
          showSizeChanger: true,
          showTotal: (total) => `${t('misc.total')} ${total}`,
        }}
      />
      <Drawer
        title={t(currentConsumer ? "consumer.edit" : "consumer.create")}
        placement="right"
        width={660}
        onClose={handleDrawerCancel}
        open={openDrawer}
        extra={
          <Space>
            <Button onClick={handleDrawerCancel}>{t('misc.cancel')}</Button>
            <Button type="primary" onClick={handleDrawerOK}>
              {t('misc.confirm')}
            </Button>
          </Space>
        }
      >
        <ConsumerForm ref={formRef} value={currentConsumer} />
      </Drawer>
      <Modal
        title={<div><ExclamationCircleOutlined style={{ color: '#ffde5c', marginRight: 8 }} />{t('misc.delete')}</div>}
        open={openModal}
        onOk={handleModalOk}
        confirmLoading={confirmLoading}
        onCancel={handleModalCancel}
        cancelText={t('misc.cancel')}
        okText={t('misc.confirm')}
      >
        <p>
          <Trans t={t} i18nKey="consumer.deleteConfirmation">
            确定删除 <span style={{ color: '#0070cc' }}>{{ currentConsumerName: (currentConsumer && currentConsumer.name) || '' }}</span> 吗？
          </Trans>
        </p>
      </Modal>

      {/* Member management Drawer */}
      <Drawer
        title={`${t('consumer.memberManagement')} - ${memberConsumer?.name || ''}`}
        placement="right"
        width={480}
        onClose={() => { setMemberDrawerVisible(false); setMemberConsumer(null); }}
        open={memberDrawerVisible}
        extra={
          <Space>
            <Button onClick={() => { setMemberDrawerVisible(false); setMemberConsumer(null); }}>
              {t('misc.cancel')}
            </Button>
            <Button type="primary" onClick={() => setAddMemberVisible(true)}>
              {t('consumer.addMember')}
            </Button>
          </Space>
        }
      >
        <Table
          loading={membersLoading}
          dataSource={consumerMembers.map((u) => {
            const matchedUser = allUsers.find((user: any) => user.name === u);
            return { username: u, displayName: matchedUser?.displayName || '-' };
          })}
          columns={[
            { title: t('consumer.memberUsername'), dataIndex: 'username', key: 'username' },
            { title: t('userManagement.displayName'), dataIndex: 'displayName', key: 'displayName' },
            {
              title: t('misc.actions'),
              key: 'action',
              width: 80,
              align: 'center' as const,
              render: (_: any, record: any) => (
                <a onClick={() => handleRemoveMember(record.username)}>{t('consumer.removeMember')}</a>
              ),
            },
          ]}
          rowKey="username"
          pagination={false}
        />
      </Drawer>

      {/* Add member Modal */}
      <Modal
        title={t('consumer.addMember')}
        open={addMemberVisible}
        onOk={handleAddMembers}
        onCancel={() => { setAddMemberVisible(false); setSelectedUsernames([]); }}
        okText={t('misc.confirm')}
        cancelText={t('misc.cancel')}
      >
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          placeholder={t('consumer.selectUsers')}
          value={selectedUsernames}
          onChange={setSelectedUsernames}
          options={availableUsers.map((u: any) => ({
            label: `${u.displayName || u.name} (${u.name})`,
            value: u.name,
          }))}
        />
      </Modal>
    </PageContainer>
  );
};

export default ConsumerList;
