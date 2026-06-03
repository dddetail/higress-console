import type { UserListItem } from '@/interfaces/user';
import { deleteUser, listUsers, updateUserRole, updateUserStatus } from '@/services/user';
import { PageContainer } from '@ant-design/pro-layout';
import { ProTable } from '@ant-design/pro-table';
import type { ActionType, ProColumns } from '@ant-design/pro-table';
import { Button, message, Modal, Popconfirm, Select, Space, Tag } from 'antd';
import React, { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

const UserList: React.FC = () => {
  const { t } = useTranslation();
  const actionRef = useRef<ActionType>();
  const [roleModalVisible, setRoleModalVisible] = useState(false);
  const [roleTarget, setRoleTarget] = useState<UserListItem | null>(null);
  const [selectedRole, setSelectedRole] = useState<string>('');

  const handleToggleStatus = async (username: string, currentStatus: string) => {
    const newStatus = currentStatus === 'active' ? 'disabled' : 'active';
    try {
      await updateUserStatus(username, newStatus);
      message.success(t('misc.updateSuccess'));
      actionRef.current?.reload();
    } catch (e) {
      message.error(t('misc.updateFailed'));
    }
  };

  const handleDelete = async (username: string) => {
    try {
      await deleteUser(username);
      message.success(t('misc.deleteSuccess'));
      actionRef.current?.reload();
    } catch (e) {
      message.error(t('misc.deleteFailed'));
    }
  };

  const columns: Array<ProColumns<UserListItem>> = [
    {
      title: t('userManagement.username'),
      dataIndex: 'name',
      key: 'name',
      copyable: true,
      ellipsis: true,
    },
    {
      title: t('userManagement.displayName'),
      dataIndex: 'displayName',
      key: 'displayName',
    },
    {
      title: t('userManagement.employeeId'),
      dataIndex: 'employeeId',
      key: 'employeeId',
      search: false,
    },
    {
      title: t('userManagement.type'),
      dataIndex: 'type',
      key: 'type',
      search: false,
      valueEnum: {
        platform_admin: { text: t('userManagement.platformAdmin') },
        consumer_user: { text: t('userManagement.consumerUser') },
      },
    },
    {
      title: t('userManagement.status'),
      dataIndex: 'status',
      key: 'status',
      search: false,
      render: (_, record) =>
        (record.status === 'active'
          ? <Tag color="green">{t('userManagement.active')}</Tag>
          : <Tag color="red">{t('userManagement.disabled')}</Tag>
        ),
    },
    {
      title: t('user.role'),
      dataIndex: 'role',
      key: 'role',
      search: false,
      valueEnum: {
        platform_admin: { text: t('user.roleAdmin'), status: 'Error' },
        owner: { text: t('user.roleOwner'), status: 'Warning' },
        manager: { text: t('user.roleManager'), status: 'Processing' },
        reader: { text: t('user.roleReader'), status: 'Default' },
      },
    },
    {
      title: t('userManagement.actions'),
      key: 'actions',
      search: false,
      render: (_, record) => (
        <Space>
          <a onClick={() => handleToggleStatus(record.name, record.status)}>
            {record.status === 'active'
              ? t('userManagement.disableUser')
              : t('userManagement.enableUser')}
          </a>
          <Popconfirm
            title={t('userManagement.deleteConfirm')}
            onConfirm={() => handleDelete(record.name)}
            okText={t('misc.confirm')}
            cancelText={t('misc.cancel')}
          >
            <a style={{ color: 'red' }}>{t('userManagement.deleteUser')}</a>
          </Popconfirm>
          <a
            onClick={() => {
              setRoleTarget(record);
              setSelectedRole(record.role || 'reader');
              setRoleModalVisible(true);
            }}
          >
            {t('user.assignRole')}
          </a>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer>
      <ProTable<UserListItem>
        columns={columns}
        actionRef={actionRef}
        request={async (params) => {
          try {
            const data = await listUsers();
            return {
              data: data || [],
              success: true,
              total: data?.length || 0,
            };
          } catch (e) {
            return {
              data: [],
              success: false,
              total: 0,
            };
          }
        }}
        rowKey="name"
        search={false}
        pagination={{
          pageSize: 20,
        }}
      />
      <Modal
        title={t('user.assignRole')}
        open={roleModalVisible}
        onOk={async () => {
          if (roleTarget && selectedRole) {
            try {
              await updateUserRole(roleTarget.name, selectedRole);
              message.success(t('misc.updateSuccess'));
              setRoleModalVisible(false);
              actionRef.current?.reload();
            } catch (e) {
              message.error(t('misc.updateFailed'));
            }
          }
        }}
        onCancel={() => setRoleModalVisible(false)}
        okText={t('misc.confirm')}
        cancelText={t('misc.cancel')}
      >
        <p>
          {roleTarget?.displayName || roleTarget?.name} - {t('user.assignRole')}
        </p>
        <Select
          style={{ width: '100%' }}
          value={selectedRole}
          onChange={setSelectedRole}
          options={[
            { label: t('user.roleAdmin'), value: 'platform_admin' },
            { label: t('user.roleOwner'), value: 'owner' },
            { label: t('user.roleManager'), value: 'manager' },
            { label: t('user.roleReader'), value: 'reader' },
          ]}
        />
      </Modal>
    </PageContainer>
  );
};

export default UserList;
