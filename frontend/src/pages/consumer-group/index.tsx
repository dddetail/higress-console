import type { ConsumerGroup } from '@/interfaces/user';
import PermissionGuard from '@/components/PermissionGuard';
import {
  createConsumerGroup,
  deleteConsumerGroup,
  listConsumerGroups,
  updateConsumerGroup,
} from '@/services/consumer-group';
import { ExclamationCircleOutlined, RedoOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-layout';
import { useRequest } from 'ahooks';
import { Button, Drawer, Form, Input, message, Modal, Space, Table } from 'antd';
import React, { useEffect, useState } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { history } from 'ice';

const ConsumerGroupList: React.FC = () => {
  const { t } = useTranslation();

  const [form] = Form.useForm();
  const [allGroups, setAllGroups] = useState<ConsumerGroup[]>([]);
  const [currentGroup, setCurrentGroup] = useState<ConsumerGroup | null>(null);
  const [openDrawer, setOpenDrawer] = useState(false);
  const [openModal, setOpenModal] = useState(false);
  const [confirmLoading, setConfirmLoading] = useState(false);

  const columns = [
    {
      title: t('consumerGroup.nameCn'),
      dataIndex: 'nameCn',
      key: 'nameCn',
      ellipsis: true,
    },
    {
      title: t('consumerGroup.nameEn'),
      dataIndex: 'nameEn',
      key: 'nameEn',
      ellipsis: true,
    },
    {
      title: t('consumerGroup.shortName'),
      dataIndex: 'shortName',
      key: 'shortName',
    },
    {
      title: t('consumerGroup.description'),
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (val: string) => val || '-',
    },
    {
      title: t('consumerGroup.status'),
      dataIndex: 'status',
      key: 'status',
      render: (val: string) => (val === 'active' ? t('consumerGroup.statusActive') : t('consumerGroup.statusDisabled')),
    },
    {
      title: t('misc.actions'),
      dataIndex: 'action',
      key: 'action',
      width: 180,
      align: 'center',
      render: (_: any, record: ConsumerGroup) => (
        <Space size="small">
          <a onClick={() => history?.push(`/consumer-group/${record.id}`)}>{t('consumerGroup.viewDetail')}</a>
          <a onClick={() => onEditDrawer(record)}>{t('misc.edit')}</a>
          <a onClick={() => onShowModal(record)}>{t('misc.delete')}</a>
        </Space>
      ),
    },
  ];

  const { loading, refresh } = useRequest(listConsumerGroups, {
    manual: true,
    onSuccess: (result) => {
      const groups = (result || []) as ConsumerGroup[];
      setAllGroups(groups);
    },
  });

  useEffect(() => {
    refresh();
  }, []);

  const onEditDrawer = (group: ConsumerGroup) => {
    setCurrentGroup(group);
    form.setFieldsValue(group);
    setOpenDrawer(true);
  };

  const onShowDrawer = () => {
    setCurrentGroup(null);
    form.resetFields();
    setOpenDrawer(true);
  };

  const handleDrawerOK = async () => {
    try {
      const values = await form.validateFields();
      if (currentGroup) {
        await updateConsumerGroup(currentGroup.id, values);
        message.success(t('consumerGroup.updateSuccess'));
      } else {
        await createConsumerGroup(values);
        message.success(t('consumerGroup.createSuccess'));
      }
      setOpenDrawer(false);
      form.resetFields();
      setCurrentGroup(null);
      refresh();
    } catch (err) {
      // handled by interceptor
    }
  };

  const handleDrawerCancel = () => {
    setOpenDrawer(false);
    form.resetFields();
    setCurrentGroup(null);
  };

  const onShowModal = (group: ConsumerGroup) => {
    setCurrentGroup(group);
    setOpenModal(true);
  };

  const handleModalOk = async () => {
    if (!currentGroup) return;
    setConfirmLoading(true);
    try {
      await deleteConsumerGroup(currentGroup.id);
      message.success(t('consumerGroup.deleteSuccess'));
    } catch (error) {
      // error handled by interceptor
    }
    setConfirmLoading(false);
    setOpenModal(false);
    setCurrentGroup(null);
    refresh();
  };

  const handleModalCancel = () => {
    setOpenModal(false);
    setCurrentGroup(null);
  };

  return (
    <PageContainer>
      <div style={{ background: '#fff', padding: '24px', marginBottom: 16 }}>
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <div />
          <Space>
            <PermissionGuard allowedRoles={['platform_admin', 'owner', 'manager']}>
              <Button type="primary" onClick={onShowDrawer}>
                {t('consumerGroup.create')}
              </Button>
            </PermissionGuard>
            <Button icon={<RedoOutlined />} onClick={refresh} />
          </Space>
        </Space>
      </div>
      <Table
        loading={loading}
        dataSource={allGroups}
        columns={columns}
        rowKey="id"
        pagination={{
          showSizeChanger: true,
          showTotal: (total) => `${t('misc.total')} ${total}`,
        }}
      />
      <Drawer
        title={t(currentGroup ? 'consumerGroup.edit' : 'consumerGroup.create')}
        placement="right"
        width={480}
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
        <Form form={form} layout="vertical">
          <Form.Item name="nameCn" label={t('consumerGroup.nameCn')} rules={[{ required: true }]}>
            <Input placeholder={t('consumerGroup.nameCn')} />
          </Form.Item>
          <Form.Item name="nameEn" label={t('consumerGroup.nameEn')} rules={[{ required: true }]}>
            <Input placeholder={t('consumerGroup.nameEn')} />
          </Form.Item>
          <Form.Item name="shortName" label={t('consumerGroup.shortName')} rules={[{ required: true }]}>
            <Input placeholder={t('consumerGroup.shortName')} disabled={!!currentGroup} />
          </Form.Item>
          <Form.Item name="description" label={t('consumerGroup.description')}>
            <Input.TextArea rows={3} placeholder={t('consumerGroup.description')} />
          </Form.Item>
        </Form>
      </Drawer>
      <Modal
        title={
          <div>
            <ExclamationCircleOutlined style={{ color: '#ffde5c', marginRight: 8 }} />
            {t('misc.delete')}
          </div>
        }
        open={openModal}
        onOk={handleModalOk}
        confirmLoading={confirmLoading}
        onCancel={handleModalCancel}
        cancelText={t('misc.cancel')}
        okText={t('misc.confirm')}
      >
        <p>
          <Trans t={t} i18nKey="consumerGroup.deleteConfirm">
            确定删除消费者组
            <span style={{ color: '#0070cc' }}>
              {{ name: (currentGroup && currentGroup.nameCn) || '' }}
            </span>
            吗？
          </Trans>
        </p>
      </Modal>
    </PageContainer>
  );
};

export default ConsumerGroupList;
